# log-guard architecture

Design record for v0.1. Published version:
https://claude.ai/code/artifact/b26775b2-230b-4d55-9d63-4b8f4422440c

---

## The problem

Every privacy code scanner looks for personal data flowing into a logging sink. In Spring Boot,
the most common leak has no personal data at the sink at all.

```java
@Entity
@Data
public class Customer {
    private Long id;
    private String email;
    private String phoneNumber;
    private LocalDate dateOfBirth;
}

log.info("Processing customer {}", customer);
```

There is no PII-named variable on that log line. The leak lives in Lombok's generated
`toString()`, one file away:

```
Processing customer Customer(id=42, email=jane.wanjiru@acme.io, phoneNumber=+254712345891, dateOfBirth=1994-03-11)
```

Catching this statically means resolving the argument's type, walking to the entity, checking
whether Lombok generated a `toString()`, and checking for `@ToString.Exclude`. Generic scanners
don't. The same structural blindness covers entities in `ProblemDetail` responses,
`MDC.put("email", ...)`, Hibernate bind parameters at `TRACE`, and request bodies logged by
`RestClient` at `DEBUG`.

log-guard does not try to find those statically. It makes them safe at runtime.

---

## Central decision: mask the event, not the layout

A custom `%mask` conversion word in the Logback `<pattern>` is the natural first instinct, and it
is a trap. The OpenTelemetry Logback appender does not render your layout — it reads
`event.getFormattedMessage()` directly.

Layout-level masking would redact the terminal you are watching and ship raw personal data to the
collector. A leak that looks fixed in exactly the place you would check.

```
Layout-level masking (wrong)          Event-level masking (right)

log.info("{}", customer)              log.info("{}", customer)
        |                                     |
   ILoggingEvent                        MaskingWrapper     wraps the event,
   (raw entity)                                |           masks nothing yet
        |                                AsyncAppender     queues it
   +----+----+                                 |
   |         |                            +----+----+      masking runs here,
Console    OTLP                           |         |      on the worker
%mask      RAW PII                     Console    OTLP
                                       masked     masked
```

The wrapper sits **above** the async boundary, but the masking does not run there.
`MaskingLoggingEvent` masks lazily and deliberately does not mask in
`prepareForDeferredProcessing()` — the one method `AsyncAppender` calls on the caller's thread. The
reflection and the regex therefore execute on the async worker, when an encoder first reads the
message.

Putting the wrapper *inside* the async appender is not possible through Logback's public API:
`AsyncAppenderBase.detachAppender()` detaches the child but never decrements the internal
`appenderCount`, so the one permitted child slot stays consumed and the replacement is refused with
a status warning. Wrapping above it reaches the same place by a different route.

Two consequences, both real and both belonging in the README. Unredacted events sit briefly in the
in-memory queue holding live references to your objects; they are never written anywhere. And
because masking is deferred, an argument mutated between the log call and the queue flush renders
in its later state.

---

## Two masking layers

| Layer | Mechanism | Catches | Cannot catch |
|---|---|---|---|
| Type-aware | Reads cached `@Pii` metadata for each argument's class, renders a masked `toString()` | Your own domain objects | Anything already formatted into a string, or produced by code you don't own |
| Pattern | Regex over the formatted message, behind a cheap prefilter | Hibernate bind parameters, third-party output, hand-concatenated strings | Unstructured PII — no regex finds a surname |

Neither is sufficient alone. They are toggled independently because their false-positive profiles
differ.

Type-aware metadata is cached in a `ClassValue<PiiMetadata>` and is effectively free after first
touch. Built-in patterns compile into a single alternation gated behind an `indexOf` prefilter, so
lines with no `@` and no long digit run skip the regex entirely.

---

## Annotation model

```java
@Retention(RUNTIME)
@Target({FIELD, RECORD_COMPONENT, METHOD})
public @interface Pii {
    MaskStrategy strategy() default REDACT;
    PiiCategory category() default PERSONAL;
}
```

| Strategy | Output | Use for |
|---|---|---|
| `REDACT` | `***` | Default. Anything you never need to see. |
| `PARTIAL` | `+2547****891` | Support workflows — enough to confirm a record with a caller. |
| `HASH` | `#a3f91c` | Correlating one user across a request trace without learning who they are. |
| `DROP` | *(omitted)* | Field left out of the output entirely. |

`HASH` is what gets this accepted in production. Without it, ops rejects the library the first
time they cannot trace an incident across services.

**Fail-fast rule.** If any `HASH` strategy is in use and `log-guard.hash-salt` is unset, startup
fails. A per-boot random salt would silently break cross-instance correlation; no salt at all
makes an email address trivially reversible with a rainbow table.

---

## Five channels masked per event

| Channel | Leak it closes |
|---|---|
| `getArgumentArray()` | The Lombok `toString()` path — the headline case |
| `getFormattedMessage()` | Concatenated strings and third-party output |
| `getMDCPropertyMap()` | `MDC.put("email", ...)` — leaks into every subsequent line on that thread |
| `getKeyValuePairs()` | SLF4J 2 structured logging |
| `IThrowableProxy` | Entities in exception messages, through the full cause chain |

---

## Module layout

```
log-guard/
├── log-guard-core/                  # annotations + engine, zero Spring, zero dependencies
├── log-guard-logback/               # event wrapping, logback-classic provided
└── log-guard-spring-boot-starter/   # autoconfig, properties, validator
```

Core stays Spring-free so the engine is unit-testable with no application context and usable from
plain Java. `slf4j-api` and `logback-classic` are `provided`; the starter adds
`spring-boot-autoconfigure` and `spring-boot-configuration-processor`. Regex, `MessageDigest` and
reflection are all JDK.

A privacy library that drags in transitive dependencies is a much harder sell to the security team
that has to approve it. Zero new dependencies is a feature, and it stays a hard rule through v0.3.

---

## Wiring

Auto-configuration walks the `LoggerContext` at startup and wraps each attached appender. No
`logback.xml` changes — the manual alternative is "add a wrapper appender and remember to re-point
every `<appender-ref>`", whose failure mode is silent unredacted output. Escape hatch is
`log-guard.enabled: false`.

---

## Configuration surface

```yaml
log-guard:
  enabled: true
  hash-salt: ${LOG_GUARD_SALT}
  type-aware:
    enabled: true
  patterns:
    enabled: true
    built-in: [EMAIL, IBAN, CREDIT_CARD, PHONE_E164, KENYAN_NATIONAL_ID]
    custom:
      - name: internal-account
        regex: 'ACC-\d{10}'
        strategy: PARTIAL
  validation:
    unannotated-entity: warn   # off | warn | fail
```

---

## The startup validator

Runtime masking reduces blast radius. The validator is what fixes the Lombok problem at its source.

At startup it scans `@Entity` classes in the application's base package. If a class has a generated
`toString()` and a field whose name matches the PII taxonomy but carries neither `@Pii` nor
`@ToString.Exclude`, it reports.

| Mode | Behaviour | Where |
|---|---|---|
| `warn` | Logs the offending class and field at `WARN` | Default. Failing an app on a dependency upgrade is hostile. |
| `fail` | Refuses to start the context | Documented for `application-test.yml`, so CI catches new entities. |
| `off` | No scan, no startup cost | Apps that have already adopted. |

---

## What this does not do

- A bare name or street address logged as a plain string is undetectable. No regex finds "Jane Wanjiru".
- `System.out.println` bypasses the pipeline entirely.
- It does not scan your code. It changes what reaches the appender at runtime.
- It reduces blast radius. It is not permission to log the object.

---

## Build order

| Version | Contents | Milestone |
|---|---|---|
| v0.1 | Core engine, `@Pii`, Logback wrapper, auto-config, built-in patterns | The Hibernate bind-parameter demo runs |
| v0.2 | MDC, key-value pairs, throwable masking, startup validator | Safe to adopt in a real service |
| v0.3 | Log4j2 `RewritePolicy`, JMH benchmarks, Jackson module | Publishable to Maven Central |
