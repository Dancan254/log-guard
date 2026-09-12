<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/wordmark-dark.svg">
  <img src="docs/assets/wordmark-light.svg" alt="log-guard" width="340">
</picture>

**Personal data never reaches the appender.**

A Spring Boot starter that masks personal data at the Logback event level,
so console, file and OTLP exports all see the same redacted output.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.dancan254/log-guard-spring-boot-starter?style=flat-square&color=f0196a&label=maven%20central)](https://central.sonatype.com/artifact/io.github.dancan254/log-guard-spring-boot-starter)
[![Build](https://img.shields.io/github/actions/workflow/status/Dancan254/log-guard/ci.yml?branch=main&style=flat-square&label=build)](https://github.com/Dancan254/log-guard/actions/workflows/ci.yml)
[![Javadoc](https://img.shields.io/badge/javadoc-latest-blue?style=flat-square)](https://javadoc.io/doc/io.github.dancan254/log-guard-core)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)](LICENSE)
[![Java](https://img.shields.io/badge/java-25-orange?style=flat-square)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/spring%20boot-4.1-6DB33F?style=flat-square)](https://spring.io/projects/spring-boot)

</div>

---

**New here?** [Installation](#installation) → [Quick start](#quick-start) →
[Configuration reference](#configuration-reference). If something is not masked,
[Troubleshooting](#troubleshooting) covers the usual causes.

<details>
<summary>Full contents</summary>

- [The problem](#the-problem)
- [Why the event and not the layout](#why-the-event-and-not-the-layout)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Two layers, both necessary](#two-layers-both-necessary)
- [What gets masked](#what-gets-masked)
- [Masking strategies](#masking-strategies)
- [The `@Pii` annotation](#the-pii-annotation)
- [Nested objects](#nested-objects)
- [Pattern masking](#pattern-masking)
- [The startup validator](#the-startup-validator)
- [Configuration reference](#configuration-reference)
- [Recipes](#recipes) — [OpenTelemetry](#exporting-to-opentelemetry) ·
  [Log4j2](#log4j2-instead-of-logback) · [JSON logs](#json-encoded-logs) ·
  [MDC keys](#redacting-mdc-keys-wholesale)
- [Try it](#try-it)
- [Troubleshooting](#troubleshooting)
- [Limitations](#limitations)
- [Performance](#performance)
- [Modules](#modules)
- [Building](#building)
- [Contributing](#contributing)
- [Licence](#licence)

</details>

## The problem

Every logging framework will print whatever you hand it. An entity with a generated `toString`
puts a customer's email, phone number and date of birth into your log aggregator, and nobody
notices until an audit.

```java
@Entity
@Data
public class Customer {
    @Id private Long id;
    private String email;
    private String phoneNumber;
    private LocalDate dateOfBirth;
}

log.info("Registered customer {}", customer);
```

```
Registered customer Customer(id=3, email=jane.wanjiru@acme.io, phoneNumber=+254712345891, dateOfBirth=1994-07-02)
```

Annotate the fields, add the starter, and the same statement produces this instead. The output
below is pasted from a run of the demo module, not from documentation.

```java
@Pii(strategy = HASH)    private String email;
@Pii(strategy = PARTIAL) private String phoneNumber;
@Pii                     private LocalDate dateOfBirth;
```

```
Registered customer Customer(id=3, email=#47f5f4, phoneNumber=+2547****244, dateOfBirth=***, city=Kisumu)
```

## Why the event and not the layout

Most redaction libraries rewrite the pattern layout. That protects the console and nothing else.
The OpenTelemetry Logback appender reads `event.getFormattedMessage()` directly, so a layout filter
leaves your OTLP pipeline carrying the raw values.

log-guard masks the logging event itself, above the async boundary, so every appender downstream
receives the same redacted text.

```mermaid
flowchart LR
    L["Application logger"] --> W["log-guard<br/>masking wrapper"]
    W --> A["AsyncAppender"]
    A --> C["Console"]
    A --> F["File"]
    A --> O["OTLP exporter"]

    style W fill:#f0196a,stroke:#f0196a,color:#ffffff
```

Masking still runs on the async worker rather than the request thread, because the wrapped event
masks lazily.

## Installation

**Maven**

```xml
<dependency>
    <groupId>io.github.dancan254</groupId>
    <artifactId>log-guard-spring-boot-starter</artifactId>
    <version>0.1.1</version>
</dependency>
```

**Gradle**

```kotlin
implementation("io.github.dancan254:log-guard-spring-boot-starter:0.1.1")
```

That is the only artifact you declare. Logback comes from your application, there is no appender to
register and nothing to switch on. The other modules are pulled in for you, except the two
alternative backends: `log-guard-log4j2` and `log-guard-jackson` are opt-in.

**Requirements**

| | |
| :--- | :--- |
| Java | 25 or later |
| Spring Boot | 4.0 or later |
| Logging backend | Logback (default) or Log4j2 |

The published jars are Java 25 bytecode and compile against Spring Boot 4 APIs, so they cannot be
dropped into a Boot 3 or Java 17 application. No backport is published. If you are still on Boot 3,
the masking engine in `log-guard-core` has no Spring dependency and can be driven directly, but you
would be wiring the Logback adapter yourself.

API documentation is published with every release:
[javadoc.io/doc/io.github.dancan254/log-guard-core](https://javadoc.io/doc/io.github.dancan254/log-guard-core).

## Quick start

**1. Annotate the fields that hold personal data.**

The whole public API lives in one package, so one import covers it:

```java
import io.github.dancan254.logguard.Pii;
import io.github.dancan254.logguard.MaskStrategy;

public record Customer(
        Long id,
        @Pii(strategy = MaskStrategy.HASH)    String email,
        @Pii(strategy = MaskStrategy.PARTIAL) String phoneNumber,
        @Pii                                  LocalDate dateOfBirth,
        String city) {
}
```

**2. Set a salt, because this example uses `HASH`.**

```yaml
log-guard:
  hash-salt: ${LOG_GUARD_SALT}
```

**3. Log the object.**

```java
log.info("Registered customer {}", customer);
```

That is the whole integration. `smoke/verify.sh` in this repository builds a Boot application with
that one dependency and asserts both masking layers run.

## Two layers, both necessary

```mermaid
flowchart TD
    E["Logging event"] --> T["Type-aware layer"]
    E --> P["Pattern layer"]

    T --> T1["Reads cached @Pii metadata"]
    T --> T2["Exact, no false positives"]
    T --> T3["Sees only your own classes"]

    P --> P1["Regex over the formatted message"]
    P --> P2["Catches Hibernate binds and driver errors"]
    P --> P3["Cannot see unstructured text"]

    style T fill:#12121f,stroke:#f0196a,color:#ffffff
    style P fill:#12121f,stroke:#f0196a,color:#ffffff
```

The type-aware layer is precise but only knows about classes you annotated. The pattern layer
covers output from code you do not own. Hibernate's bind-parameter trace and a driver's own
exception message are both real leak paths that no annotation can reach:

```
org.hibernate.orm.jdbc.bind - binding parameter (3:VARCHAR) <- [***]

Caused by: java.sql.SQLException: duplicate key value violates unique
constraint: Key (email)=(***) already exists
```

Both layers are enabled by default.

## What gets masked

Five channels on every event, because personal data leaks from all of them.

| Channel | Example |
| :--- | :--- |
| `getArgumentArray()` | `log.info("{}", customer)` |
| `getFormattedMessage()` | the rendered line, scanned by the pattern layer |
| `getMDCPropertyMap()` | `MDC.put("userEmail", ...)` |
| `getKeyValuePairs()` | `atInfo().addKeyValue("email", ...)` |
| `IThrowableProxy` | a constraint violation quoting the offending value |

The throwable channel is the subtle one. The OpenTelemetry appender exports an exception only when
it can pull a real `Throwable` from the event, so log-guard hands it a masked stand-in. An exception
whose chain held nothing to mask is passed through untouched, which keeps `exception.type` exact
wherever masking changed nothing.

## Masking strategies

| Strategy | Output | Use for |
| :--- | :--- | :--- |
| `REDACT` (default) | `***` | Values with no analytical use |
| `PARTIAL` | `j****@example.com`, `+2547****244` | Support staff need to recognise the record |
| `HASH` | `#47f5f4` | Correlating a user across log lines without storing the value |
| `DROP` | field omitted entirely | Fields that should never appear |

`PARTIAL` is length aware, because a mask that shrinks with its input leaks the input:

* Contains `@`: first character, `****`, then everything from the `@`.
* 12 characters or more: first 5, `****`, last 3.
* 8 to 11 characters: `****`, last 3.
* Shorter than 8: falls back to `***`, since there is not enough length to hide anything in.

`HASH` is a salted SHA-256 truncated to 6 hex characters, stable across lines and instances so one
user stays correlatable. It requires `log-guard.hash-salt`. Rotating the salt breaks correlation
with older logs, which is the intended trade. Treat the salt as a secret and inject it.

With a blank salt, `MissingHashSaltException` is thrown at startup for a custom pattern using
`HASH`, and for an `@Entity` class using `HASH` when the startup validator finds it. Outside those
cases the value falls back to `***`, so set the salt whenever you use `HASH` and leave the
validator enabled.

## The `@Pii` annotation

```java
@Pii(strategy = MaskStrategy.PARTIAL, category = PiiCategory.PERSONAL)
```

It applies to a field or a record component. Both attributes are optional, and a bare `@Pii` means
`REDACT` and `PERSONAL`.

`category` does not change the output. It is metadata for your own auditing, recording which fields
hold financial data and which hold credentials. Values are `PERSONAL` (default), `SENSITIVE`,
`FINANCIAL`, `HEALTH` and `CREDENTIAL`.

A class is rendered field by field only when it carries at least one `@Pii`. Everything else is left
to its own `toString()`.

> **Note**
> Lombok's `@ToString.Exclude` is source retained and invisible at runtime, so it cannot hide a
> field from log-guard. Use `@Pii(strategy = DROP)` instead.

## Nested objects

An annotated object inside another object is rendered recursively across objects, arrays,
collections and maps, with three limits that stop a log statement becoming a graph traversal:

* Depth of 3.
* 10 elements per collection or array.
* An identity cycle guard, so a bidirectional JPA relationship terminates.

Reflection only enters classes that carry `@Pii`. A class with no annotation of its own is still
rendered when one of its declared field types carries `@Pii`, because a field typed `Object` hides
whatever it actually holds.

```yaml
log-guard:
  nesting:
    base-packages: [com.example.domain]
```

## Pattern masking

| Pattern | Matches | Default |
| :--- | :--- | :--- |
| `EMAIL` | addresses | Enabled |
| `IBAN` | international bank account numbers | Enabled |
| `CREDIT_CARD` | 13 to 19 digits, Luhn checked | Enabled |
| `PHONE_E164` | `+` followed by 8 to 15 digits | Enabled |
| `KENYAN_NATIONAL_ID` | 7 or 8 digit runs | Disabled |

`KENYAN_NATIONAL_ID` is off by default because a bare 7 or 8 digit run is also an order number, a
row count and a port. Enable it only where you know your log lines.

```yaml
log-guard:
  patterns:
    built-in: [EMAIL, IBAN, CREDIT_CARD, PHONE_E164, KENYAN_NATIONAL_ID]
    custom:
      - name: employee-id
        regex: "EMP-\\d{6}"
        strategy: REDACT
```

Two safety properties are worth understanding. A flat ASCII prefilter runs first, so a line
containing no trigger character never reaches a regex, which is why a clean line costs 126 ns rather
than microseconds. And `max-message-length` (8192) caps what is scanned. Past the cap the head is
masked and the tail is replaced with a truncation notice, with the cut moved back to a token
boundary so a value straddling the limit cannot escape as an unmatched fragment. It fails closed,
because skipping the regex on long input is a leak anyone can trigger by padding a field.

## The startup validator

At startup log-guard scans `@Entity` classes in your own packages and reports any class that
declares a `toString()` and holds a field whose name is in the personal data taxonomy but carries no
`@Pii`. Inherited fields are included, so the usual `@MappedSuperclass` layout is covered. It reads
annotations by name through Spring's metadata reader, so the starter needs no JPA dependency.

```yaml
log-guard:
  validation:
    unannotated-entity: WARN   # OFF | WARN | FAIL
```

Set `FAIL` in CI and a new unannotated `email` column cannot reach production.

## Configuration reference

```yaml
log-guard:
  enabled: true
  hash-salt: ""                 # required if any field uses HASH
  on-failure: PLACEHOLDER       # or DROP, PASSTHROUGH
  type-aware:
    enabled: true
  patterns:
    enabled: true
    built-in: [EMAIL, IBAN, CREDIT_CARD, PHONE_E164]
    max-message-length: 8192
    custom: []                  # name, regex, strategy
  mdc:
    redact-keys: []             # emptied whatever they hold, matched case insensitively
  nesting:
    max-depth: 3
    max-elements: 10
    base-packages: []           # empty means any class carrying @Pii
  validation:
    unannotated-entity: WARN    # or FAIL for CI, OFF to skip
```

`on-failure` decides what an appender does with an event log-guard could not mask.

| Mode | Behaviour |
| :--- | :--- |
| `PLACEHOLDER` (default) | Keep the event, replace its payload with a notice. Never leaks, never silent. |
| `DROP` | Discard the event. Choose this when a leak matters more than a missing line. |
| `PASSTHROUGH` | Emit unmasked. Only when the log is already inside your trust boundary. |

## Recipes

### Exporting to OpenTelemetry

Boot's OTel starter exports signals but never hands the SDK to the Logback appender, and
`OpenTelemetryAppender.install()` inspects only a logger's top level appenders, so it cannot see one
that log-guard has wrapped. Hand it over through the wrapper.

```java
@Configuration
@ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
public class OpenTelemetryLogbackBridge {

    private final OpenTelemetry openTelemetry;

    public OpenTelemetryLogbackBridge(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @EventListener(ApplicationReadyEvent.class)
    void install() {
        OpenTelemetryAppender.install(openTelemetry);
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext)) {
            return;
        }
        loggerContext.getLoggerList().forEach(logger ->
                logger.iteratorForAppenders().forEachRemaining(this::installIntoWrapped));
    }

    private void installIntoWrapped(Appender<ILoggingEvent> appender) {
        if (appender instanceof MaskingAppenderWrapper wrapper
                && wrapper.getDelegate() instanceof OpenTelemetryAppender otelAppender) {
            otelAppender.setOpenTelemetry(openTelemetry);
        }
    }
}
```

### Log4j2 instead of Logback

Log4j2's extension point is a `RewritePolicy` rather than appender wrapping, so the adapter differs
while the engine does not.

```xml
<dependency>
    <groupId>io.github.dancan254</groupId>
    <artifactId>log-guard-log4j2</artifactId>
    <version>0.1.1</version>
</dependency>
```

Put every appender behind the rewrite, so none of them sees the raw event.

```xml
<Rewrite name="MASKED">
    <LogGuardRewritePolicy/>
    <AppenderRef ref="CONSOLE"/>
</Rewrite>
```

Two differences apply on this backend. Key value pairs are masked by the pattern layer alone,
because SLF4J's Log4j2 binding stores pairs as `Map<String, String>` and calls `String.valueOf`
inside `addKeyValue`, so no object survives to the rewrite point. Naming the key in
`log-guard.mdc.redact-keys` closes that gap. Separately, OTLP log export does not follow you here,
because the bridge above is conditional on Logback being present. Log arguments are unaffected and
stay type aware on both backends.

### JSON encoded logs

```java
ObjectMapper logMapper = JsonMapper.builder()
        .addModule(new LogGuardModule(hashSalt))
        .build();
```

Register it on a mapper you build for logging. The module is deliberately not auto registered,
because adding it to your application's primary mapper would silently mask REST responses.

### Redacting MDC keys wholesale

```yaml
log-guard:
  mdc:
    redact-keys: [userEmail, sessionToken]
```

Matched without regard to case, and emptied whatever they hold.

## Try it

The repository ships a runnable demo with a Boot application, Postgres and an OpenTelemetry
collector, so the masking can be watched rather than taken on trust. Docker is required.

```bash
./mvnw -pl log-guard-demo spring-boot:run     # listens on 8081
```

In a second terminal, watch what actually leaves the process:

```bash
docker compose -f log-guard-demo/compose.yaml logs -f otel-collector
```

That second terminal is the one that matters. Masking a console is easy. The claim worth checking is
that OTLP carries the same redacted text.

```bash
curl -s -X POST localhost:8081/customers \
  -H 'Content-Type: application/json' \
  -d '{"email":"jane.wanjiru@acme.io","phoneNumber":"+254712345891",
       "nationalId":"31234567","dateOfBirth":"1994-03-11","city":"Nairobi"}'

curl -s localhost:8081/leaks/mdc        # MDC map
curl -s localhost:8081/leaks/kv         # key value pairs
curl -s localhost:8081/leaks/exception  # throwable chain
curl -s localhost:8081/leaks/nested     # nested object in an unannotated wrapper
```

To see the difference rather than trust it, run the same requests with masking disabled and compare
both the console and the collector.

```bash
./mvnw -pl log-guard-demo spring-boot:run \
  -Dspring-boot.run.arguments=--log-guard.enabled=false
```

## Troubleshooting

### Nothing is masked at all

log-guard installs itself before the first log line and **prints no banner**, so there is no
startup message confirming it is active. Check, in this order:

1. The starter is on the classpath. `log-guard-core` alone masks nothing on its own — the starter
   is what wires it into Logback.
2. `log-guard.enabled` is not `false`, and neither is `log-guard.type-aware.enabled`.
3. The class you are logging carries at least one `@Pii`. A class with no annotation is left to its
   own `toString()`.
4. You are passing the object, not a string. `log.info("{}", customer)` is type aware;
   `log.info("customer " + customer)` has already been rendered by the time log-guard sees it, and
   only the pattern layer can help.

The quickest confirmation is a comparison. Run your app normally, then again with masking off, and
diff a line you care about:

```bash
--log-guard.enabled=false
```

### Some lines are masked and others are not

- Anything logged **before** the starter installs — Boot's banner and its own first startup lines —
  is out of reach.
- Output from code you do not own has no annotations to read, so it depends on the pattern layer.
  Hibernate bind parameters and driver exception messages are covered; a bare name in prose is not.
- On Log4j2, SLF4J key-value pairs arrive as strings and get the pattern layer only. Name the key
  in `log-guard.mdc.redact-keys` to close that gap.

### The application will not start

| Exception | Cause |
| :--- | :--- |
| `MissingHashSaltException` | A field or custom pattern uses `HASH` with a blank `log-guard.hash-salt`. |
| `InvalidPatternException` | A custom pattern's `regex` does not compile. |
| `UnannotatedEntityException` | `log-guard.validation.unannotated-entity: FAIL` found an entity that leaks. |

### A startup warning about an entity

The validator found an `@Entity` that declares a `toString()` and holds a field whose name is in
the personal data taxonomy but carries no `@Pii`. Annotate the field, or use
`@Pii(strategy = DROP)` to leave it out of the output entirely. Lombok's `@ToString.Exclude` cannot
silence the finding, because it is source-retained and invisible at runtime.

To turn the check off, set `log-guard.validation.unannotated-entity: OFF` — but `FAIL` in CI is how
a new unannotated `email` column gets stopped before production.

### The console is masked but OTLP still carries raw data

`OpenTelemetryAppender.install()` inspects only a logger's **top-level** appenders, so it never
finds the one log-guard has wrapped. Hand it the SDK through the wrapper:
[Exporting to OpenTelemetry](#exporting-to-opentelemetry).

This is also why span exceptions stay unmasked — they never pass through a logging backend at all.

### A long line ends in a truncation notice

The pattern layer scans up to `log-guard.patterns.max-message-length` (8192). Past the cap the head
is masked and the tail is replaced, because skipping the regex on long input is a leak anyone can
trigger by padding a field. Raise the cap if your lines are legitimately longer.

### Something is masked that should not be

A built-in pattern is matching too broadly. Drop it from `log-guard.patterns.built-in` — the list
you set replaces the defaults. `KENYAN_NATIONAL_ID` is the usual culprit and ships disabled for
exactly this reason.

### A nested object is not being rendered

Nesting stops at depth 3 and 10 elements per collection, and reflection only enters classes that
carry `@Pii` or declare a field whose type does. If you set
`log-guard.nesting.base-packages`, a class outside those packages is skipped.

## Limitations

* Anything logged before the starter installs, such as Boot's banner and its own first startup
  lines, is outside reach.
* A bare name in a plain string is undetectable by either layer.
* On Log4j2, SLF4J key value pairs arrive as strings and receive the pattern layer only.
* Span exceptions never pass through a logging backend, so Micrometer exports them unmasked even
  though the same text is masked in the logs.
* This is not a compliance guarantee and not a static scanner. It changes what reaches the appender
  at runtime and never reads your code.

## Performance

JMH, via `./mvnw -Pbench -pl log-guard-benchmarks verify`. The numbers below come from a loaded
developer laptop (JDK 25, 12 cores, other containers running), so read them as an order of magnitude
and as a comparison between rows rather than a specification.

| Benchmark | ns/op |
| :--- | ---: |
| Argument with no `@Pii` anywhere (the common case) | **8** |
| A log line with nothing to mask, through the pattern layer | **126** |
| Rendering an annotated 6 field entity | 1,519 |
| Rendering a 20 field entity | 1,614 |
| Rendering a list of three annotated entities | 4,745 |
| A line that does contain an email, masked | 7,137 |
| Both layers together on one event | 25,452 |

The first row is the one that matters. An argument whose class carries no `@Pii` costs a
`ClassValue` lookup and nothing else.

CI runs the same benchmarks on every push with fewer forks and iterations, then checks each score
against a ceiling in `log-guard-benchmarks/check-thresholds.sh`. The ceilings sit about ten times
above the numbers above, because a shared runner cannot produce a publishable measurement and a
benchmark that fails on noise gets deleted within a month.

## Modules

| Module | Purpose |
| :--- | :--- |
| `log-guard-core` | `@Pii`, strategies, masking engine. Zero dependencies. |
| `log-guard-logback` | Logback appender wrapping. |
| `log-guard-spring-boot-starter` | Auto-configuration, properties, startup validator. |
| `log-guard-log4j2` | `RewritePolicy` for applications on Log4j2. |
| `log-guard-jackson` | Jackson module for JSON encoded logs. |
| `log-guard-benchmarks` | JMH harness. Never published, `-Pbench` only. |

Core is Spring free and dependency free, and stays that way. A privacy library with a transitive
dependency tree is a harder sell to the team that has to approve it.

## Building

```bash
./mvnw test        # unit and Testcontainers integration tests (Docker required)
./mvnw package     # module jars
```

## Contributing

Pull requests are welcome. [`CONTRIBUTING.md`](CONTRIBUTING.md) has the build commands, the test
conventions and the invariants a change has to respect — chiefly that `log-guard-core` stays
dependency-free and that masking happens on the event rather than in a layout.

| Document | What is in it |
| :--- | :--- |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How to build, test, commit and open a pull request. |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Why the design is shaped this way. |
| [`AGENTS.md`](AGENTS.md) | Instructions for AI coding agents working in this repository. |
| [`docs/RELEASING.md`](docs/RELEASING.md) | The Maven Central runbook. Maintainers only. |
| [`SECURITY.md`](SECURITY.md) | How to report a masking bypass privately. |
| [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) | The Contributor Covenant, and how to report a breach. |
| [`CHANGELOG.md`](CHANGELOG.md) | What changed in each release. |

Found personal data reaching an appender unmasked? That is a vulnerability rather than a bug —
please report it through the [Security tab](https://github.com/Dancan254/log-guard/security/advisories)
instead of a public issue.

## Licence

Apache-2.0. See [LICENSE](LICENSE).
