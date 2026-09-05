# log-guard

Keeps personal data out of your logs.

```java
@Entity
@Data
public class Customer {
    @Id private Long id;
    @Pii(strategy = HASH)    private String email;
    @Pii(strategy = PARTIAL) private String phoneNumber;
    @Pii                     private LocalDate dateOfBirth;
}
```

```java
log.info("Processing customer {}", customer);
```

Without log-guard, Lombok's generated `toString()` puts this in your log aggregator. With it on the
classpath — and no other configuration — the console shows this instead, pasted from a run of the
demo module:

```
i.g.d.l.demo.customer.CustomerService - Registered customer Customer(id=3, email=#47f5f4, phoneNumber=+2547****244, nationalId=***, dateOfBirth=***, city=Kisumu)
```

Masking happens on the logging **event**, not the pattern layout, so the same masked text is what
reaches an OpenTelemetry collector over OTLP:

```
otel-collector-1  | Body: Str(Registered customer Customer(id=3, email=#47f5f4, phoneNumber=+2547****244, nationalId=***, dateOfBirth=***, city=Kisumu))
```

The pattern layer catches what no annotation can — output from code you do not own. Hibernate's
bind-parameter trace, from the same run:

```
org.hibernate.orm.jdbc.bind - binding parameter (1:VARCHAR) <- [Kisumu]
org.hibernate.orm.jdbc.bind - binding parameter (2:DATE) <- [1991-07-02]
org.hibernate.orm.jdbc.bind - binding parameter (3:VARCHAR) <- [***]
```

And the channel that leaks without anyone writing a log statement — the driver's own exception:

```
Caused by: java.sql.SQLException: duplicate key value violates unique constraint: Key (email)=(***) already exists
```

---

## Contents

- [Install](#install)
- [Quick start](#quick-start)
- [Try it](#try-it)
- [How it works](#how-it-works)
- [The `@Pii` annotation](#the-pii-annotation)
- [Masking strategies](#masking-strategies)
- [What gets masked](#what-gets-masked)
- [Nested objects](#nested-objects)
- [Pattern masking](#pattern-masking)
- [The startup validator](#the-startup-validator)
- [Configuration reference](#configuration-reference)
- [Recipes](#recipes)
- [What it will not do](#what-it-will-not-do)
- [Performance](#performance)
- [Modules](#modules)

## Install

```xml
<dependency>
    <groupId>io.github.dancan254</groupId>
    <artifactId>log-guard-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Requires Java 25 and Spring Boot 4. The starter brings `log-guard-core` and `log-guard-logback`
with it; Logback itself comes from your application.

Optional companions, covered under [Recipes](#recipes): `log-guard-log4j2` for applications on
Log4j2, and `log-guard-jackson` for JSON-encoded logs.

## Quick start

**1. Annotate the fields that hold personal data.**

```java
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

```
Registered customer Customer(id=3, email=#47f5f4, phoneNumber=+2547****244, dateOfBirth=***, city=Kisumu)
```

There is nothing to switch on and no appender to declare. `smoke/verify.sh` in this repository
builds a Boot application with that one dependency and asserts both layers mask.

## Try it

This repository ships a runnable demo — a Boot application, Postgres, and an OpenTelemetry
collector — so the masking can be watched rather than taken on trust.

**Requires Docker.** `application.yml` sets `lifecycle-management: start_and_stop`, so Boot starts
the containers in `log-guard-demo/compose.yaml` itself.

```bash
./mvnw -pl log-guard-demo spring-boot:run     # listens on 8081
```

In a second terminal, watch what actually leaves the process. The collector runs the `debug`
exporter at `verbosity: detailed`, so every exported log body prints:

```bash
docker compose -f log-guard-demo/compose.yaml logs -f otel-collector
```

That second terminal is the one that matters. Masking a console is easy; the claim worth checking
is that OTLP carries the same redacted text.

### Drive it

```bash
curl -s -X POST localhost:8081/customers \
  -H 'Content-Type: application/json' \
  -d '{"email":"jane.wanjiru@acme.io","phoneNumber":"+254712345891",
       "nationalId":"31234567","dateOfBirth":"1994-03-11","city":"Nairobi"}'
```

`LeakController` then exposes one endpoint per masked channel, so each can be watched on its own:

```bash
curl -s localhost:8081/leaks/mdc        # MDC map
curl -s localhost:8081/leaks/kv         # key-value pairs
curl -s localhost:8081/leaks/exception  # throwable chain
curl -s localhost:8081/leaks/nested     # nested object inside an unannotated wrapper
```

| Request | What to look for |
|---|---|
| `POST /customers` | `Customer(id=1, email=#…, phoneNumber=+2547****891, nationalId=***, dateOfBirth=***, city=Nairobi)` |
| the same request | `binding parameter (3:VARCHAR) <- [***]` — Hibernate's own TRACE output, from code you do not own |
| `/leaks/mdc` | `actor` masked by pattern, `customerName` emptied by key, `requestId` untouched |
| `/leaks/exception` | the `SQLException` message reduced to `Key (email)=(***)` |
| `/leaks/nested` | `Order` masked despite carrying no `@Pii` of its own — it is masked for what it holds |

The demo enables `KENYAN_NATIONAL_ID` and sets `mdc.redact-keys: [customerName]`, neither of which
is on by default. That is why the 8-digit ID and the bare name are caught here and would not be in
a stock install.

### See the difference

Run it again with masking off and compare the two consoles — and the two collector streams:

```bash
./mvnw -pl log-guard-demo spring-boot:run \
  -Dspring-boot.run.arguments=--log-guard.enabled=false
```

### On Log4j2

The demo carries both logging backends behind Maven profiles. Masking runs through a
`RewritePolicy` rather than appender wrapping:

```bash
./mvnw -pl log-guard-demo spring-boot:run -Plog4j2
```

Two differences are worth watching for on this profile, both covered under
[Log4j2 instead of Logback](#log4j2-instead-of-logback): `/leaks/kv` is masked by the pattern layer
alone, and nothing reaches the collector's logs pipeline.

### Without Docker

The core tests need no containers and print the transformation directly:

```bash
./mvnw -pl log-guard-core test
```

```
before  Processing customer Customer(id=42, email=jane.wanjiru@acme.io, phoneNumber=+254712345891, dateOfBirth=1994-03-11)
after   Processing customer Customer(id=42, email=#8fad7e, phoneNumber=+2547****891, dateOfBirth=***)
```

## How it works

log-guard wraps every Logback appender in your application at startup, so masking happens once, on
the event, before any appender sees it:

```
Logger → MaskingWrapper → AsyncAppender → Console / File / OTLP
```

This placement is the central design decision. The OpenTelemetry Logback appender reads
`event.getFormattedMessage()` directly, so masking at the pattern layout would redact your console
and still export raw personal data over OTLP. Masking the event means every destination sees the
same redacted text.

Two independent layers run on each event:

| Layer | Sees | Strength | Blind spot |
|---|---|---|---|
| **Type-aware** | Objects you pass as log arguments | Exact. Reads `@Pii` metadata, no false positives | Only your own annotated classes |
| **Pattern** | The formatted message text | Catches third-party output — Hibernate binds, driver exceptions | Cannot see unstructured personal data |

Both are needed and neither is sufficient. Both are on by default.

## The `@Pii` annotation

```java
@Pii(strategy = MaskStrategy.PARTIAL, category = PiiCategory.PERSONAL)
```

It goes on a **field** or a **record component**. Both attributes are optional — bare `@Pii` means
`REDACT` and `PERSONAL`.

`category` does not change the output. It is metadata for your own auditing: which fields in this
codebase hold financial data, which hold credentials. Values are `PERSONAL` (default), `SENSITIVE`,
`FINANCIAL`, `HEALTH`, `CREDENTIAL`.

A class is only rendered field-by-field when it carries at least one `@Pii`. Anything else is left
to its own `toString()`.

> Lombok's `@ToString.Exclude` is source-retained and invisible at runtime, so it cannot hide a
> field from log-guard. Use `@Pii(strategy = DROP)` instead.

## Masking strategies

| Strategy | Output | Use for |
|---|---|---|
| `REDACT` *(default)* | `***` | Anything where the value has no analytical use |
| `PARTIAL` | `j****@example.com`, `+2547****244` | Support staff need to recognise the record |
| `HASH` | `#47f5f4` | Correlating one user across log lines without storing the value |
| `DROP` | field omitted entirely | Fields that should not appear at all |

**`PARTIAL` is length-aware**, because a mask that shrinks with the value leaks the value:

- contains `@` → first character, `****`, then everything from the `@` — `j****@example.com`
- 12 characters or more → first 5, `****`, last 3
- 8 to 11 characters → `****`, last 3
- shorter than 8 → falls back to `***`, because there is not enough length to hide anything in

**`HASH` is a salted SHA-256, truncated to 6 hex characters**, stable across lines and instances so
one user stays correlatable. It requires `log-guard.hash-salt`.

With a blank salt, `MissingHashSaltException` is thrown at startup in two cases: a custom pattern
using `HASH`, always; and an `@Entity` class using `HASH`, when the startup validator finds it.
Outside those — a `HASH` field on a class the validator never scans, or with
`validation.unannotated-entity: OFF` — there is no exception and the value falls back to `***`.
Set the salt whenever you use `HASH`, and leave the validator on so the check has teeth.

Rotating the salt breaks correlation with older logs. That is the intended trade-off — treat the
salt as a secret and inject it, never commit it.

## What gets masked

Five channels on every event, because personal data leaks from all of them:

| Channel | Example |
|---|---|
| `getArgumentArray()` | `log.info("{}", customer)` |
| `getFormattedMessage()` | the rendered line, scanned by the pattern layer |
| `getMDCPropertyMap()` | `MDC.put("userEmail", ...)` |
| `getKeyValuePairs()` | `atInfo().addKeyValue("email", ...)` |
| `IThrowableProxy` | a constraint violation quoting the offending value |

The throwable proxy is the subtle one. The OpenTelemetry appender exports an exception only when it
can pull a real `Throwable` out of the event, so log-guard hands it a masked stand-in. An exception
whose chain held nothing to mask is passed through untouched, keeping `exception.type` exact
wherever masking changed nothing.

## Nested objects

An annotated object inside another object is rendered recursively — objects, arrays, collections
and maps — with three limits that keep a log statement from becoming a graph traversal:

- **depth 3**
- **10 elements** per collection or array
- an **identity cycle guard**, so a bidirectional JPA relationship terminates

Reflection only enters classes that carry `@Pii`. A class with no annotation of its own is still
rendered when one of its **declared field types** carries `@Pii` — a field typed `Object` hides
whatever it actually holds, so the declared type is what gets checked.

Narrow it further when you want reflection confined to your own code:

```yaml
log-guard:
  nesting:
    base-packages: [com.example.domain]
```

## Pattern masking

The regex layer runs over the formatted message and catches what annotations cannot reach.

| Pattern | Matches | Enabled by default |
|---|---|---|
| `EMAIL` | addresses | yes |
| `IBAN` | international bank account numbers | yes |
| `CREDIT_CARD` | 13–19 digits, **Luhn-checked** so order numbers survive | yes |
| `PHONE_E164` | `+` followed by 8–15 digits | yes |
| `KENYAN_NATIONAL_ID` | 7–8 digit runs | **no** — opt in |

`KENYAN_NATIONAL_ID` is off by default because a bare 7–8 digit run is also an order number, a row
count and a port. Enable it only where you know your log lines:

```yaml
log-guard:
  patterns:
    built-in: [EMAIL, IBAN, CREDIT_CARD, PHONE_E164, KENYAN_NATIONAL_ID]
```

Add your own:

```yaml
log-guard:
  patterns:
    custom:
      - name: employee-id
        regex: "EMP-\\d{6}"
        strategy: REDACT
```

**Two safety properties worth understanding.** A cheap flat ASCII prefilter runs first, so a line
containing no trigger character never reaches a regex at all — that is why a clean line costs 126 ns
rather than microseconds. And `max-message-length` (8192) caps what is scanned: past the cap the
head is masked and the tail is replaced with `…[log-guard: message truncated]`. It fails closed on
purpose, because skipping the regex on long input is a leak anyone can trigger by padding a field.

## The startup validator

At startup log-guard scans `@Entity` classes in your own packages and reports any class that
declares a `toString()` and holds a field whose name is in the personal-data taxonomy but carries
no `@Pii`. It reads annotations by name through Spring's metadata reader, so the starter needs no
JPA dependency of its own.

```yaml
log-guard:
  validation:
    unannotated-entity: WARN   # OFF | WARN | FAIL
```

`WARN` by default. **Set `FAIL` in CI** and a new unannotated `email` column cannot reach
production:

```yaml
# application-ci.yml
log-guard:
  validation:
    unannotated-entity: FAIL
```

## Configuration reference

Every property, with its default:

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
    redact-keys: []             # emptied whatever they hold, matched case-insensitively
  nesting:
    max-depth: 3
    max-elements: 10
    base-packages: []           # empty means "any class carrying @Pii"
  validation:
    unannotated-entity: WARN    # or FAIL for CI, OFF to skip
```

`on-failure` decides what an appender does with an event log-guard could not mask:

| Mode | Behaviour |
|---|---|
| `PLACEHOLDER` *(default)* | Keep the event, replace its payload with a notice. Never leaks, never silent |
| `DROP` | Discard the event. Choose when a leak matters more than a missing line |
| `PASSTHROUGH` | Emit unmasked. Only when the log is already inside your trust boundary |

## Recipes

### Exporting to OpenTelemetry

Boot's OTel starter exports signals but never hands the SDK to the Logback appender, and
`OpenTelemetryAppender.install()` inspects only a logger's **top-level** appenders — so it cannot
see one log-guard has wrapped. Hand it over through the wrapper:

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

`log-guard-demo` runs exactly this against a live collector.

### Log4j2 instead of Logback

Log4j2's extension point is a `RewritePolicy` rather than appender wrapping, so the adapter differs
while the engine does not.

```xml
<dependency>
    <groupId>io.github.dancan254</groupId>
    <artifactId>log-guard-log4j2</artifactId>
    <version>0.1.0</version>
</dependency>
```

Put every appender behind the rewrite, so none of them ever sees the raw event:

```xml
<Configuration>
    <Appenders>
        <Console name="CONSOLE" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} %-5level %logger{28} mdc=%X - %msg%n%throwable"/>
        </Console>

        <Rewrite name="MASKED">
            <LogGuardRewritePolicy/>
            <AppenderRef ref="CONSOLE"/>
        </Rewrite>
    </Appenders>

    <Loggers>
        <Root level="INFO">
            <AppenderRef ref="MASKED"/>
        </Root>
    </Loggers>
</Configuration>
```

Log4j2 reads its configuration before Spring's listener runs, so the policy resolves a masker per
event. Until the starter publishes one, events pass through unmasked — the same startup window
Logback has for Boot's own banner.

**Key-value pairs are pattern-masked only on this backend, and that is not a parity you can
configure away.** SLF4J's Log4j2 binding keeps pairs as `Map<String, String>` and calls
`String.valueOf` inside `addKeyValue`, so an object is rendered by its own `toString()` at the call
site — before any event exists, and long before the rewrite policy runs. There is no object left to
inspect, so `@Pii` never applies and `HASH` and `PARTIAL` degrade to what the regexes catch:

```java
log.atInfo().addKeyValue("customer", customer).log("structured event");
```

```
logback   customer="Customer(id=1, email=#8fad7e, phoneNumber=+2547****891, dateOfBirth=***)"
log4j2    customer=Customer(id=1,  email=***,     phoneNumber=***,          dateOfBirth=1994-03-11)
```

The date survives because no built-in pattern matches one. Where a key holds personal data whatever
it contains, name it and the whole value goes — pairs arrive as context data, so the MDC list covers
them:

```yaml
log-guard:
  mdc:
    redact-keys: [customer]
```

Log arguments are unaffected: `log.info("order {}", order)` is type-aware on both backends, because
Log4j2 keeps message parameters as objects.

Boot's OTLP **log** export also does not follow you here. `OpenTelemetryLogbackBridge` is the thing
that hands the SDK to an appender, and it is conditional on Logback being present, so on Log4j2 the
console is masked and nothing reaches the collector's logs pipeline at all.

### JSON-encoded logs

```java
ObjectMapper logMapper = JsonMapper.builder()
        .addModule(new LogGuardModule(hashSalt))
        .build();
```

Register it on a mapper you build **for logging**. The module is deliberately not auto-registered:
adding it to your application's primary mapper would silently mask REST responses, which is a
spectacular way for a logging library to break someone's API.

### Redacting MDC keys wholesale

When a value is personal data whatever it contains, match the key rather than the value:

```yaml
log-guard:
  mdc:
    redact-keys: [userEmail, sessionToken]
```

Matched without regard to case, and emptied whatever they hold.

### Turning it off for one environment

```yaml
log-guard:
  enabled: false
```

Appenders are left unwrapped. Prefer this to removing the dependency, so the difference between
environments stays visible in configuration.

## What it will not do

- It cannot mask what is logged before it installs: Boot's banner and its own first startup lines.
- A bare name in a plain string is undetectable. Neither layer can see it.
- It is not a compliance guarantee, and not a static scanner. It changes what reaches the appender
  at runtime; it never reads your code.
- It does not mask **spans**. An exception recorded on a span by Micrometer never passes through
  Logback or Log4j2, so a constraint violation quoting an address is exported raw over OTLP as
  `exception.message` even though the same text is masked in the logs.
- On Log4j2, **key-value pairs** reach it as strings and get the pattern layer only. See
  [Log4j2 instead of Logback](#log4j2-instead-of-logback).

## Performance

JMH, `./mvnw -Pbench -pl log-guard-benchmarks verify`. Numbers below are from a loaded developer
laptop (JDK 25, 12 cores, other containers running), so read them as an order of magnitude and as
a comparison between rows, not as a spec sheet.

| Benchmark | ns/op |
|---|---|
| Argument with no `@Pii` anywhere (the common case) | **8** |
| A log line with nothing to mask, through the pattern layer | **126** |
| Rendering an annotated 6-field entity | 1,519 |
| Rendering a 20-field entity | 1,614 |
| Rendering a list of three annotated entities | 4,745 |
| A line that does contain an email, masked | 7,137 |
| Both layers together on one event | 25,452 |

The first row is the one that matters: an argument whose class carries no `@Pii` costs a
`ClassValue` lookup and nothing else. The pattern layer only reaches the regex when a message
actually satisfies some enabled pattern's minimum requirements — before that check existed, any
line containing a single digit paid 14 µs.

CI runs the same benchmarks on every push with fewer forks and iterations, then checks each score
against a ceiling in `log-guard-benchmarks/check-thresholds.sh`. The ceilings sit about ten times
above the numbers above, because a shared runner cannot produce a publishable measurement and a
benchmark that fails on noise gets deleted within a month. They are set to catch the shape of
regression this project has already had once, where a clean line went from 126 ns to 14,000.

## Modules

| Module | Purpose |
|---|---|
| `log-guard-core` | `@Pii`, strategies, masking engine. Zero dependencies. |
| `log-guard-logback` | Logback appender wrapping. |
| `log-guard-spring-boot-starter` | Auto-configuration, properties, startup validator. |
| `log-guard-log4j2` | `RewritePolicy` for applications on Log4j2. |
| `log-guard-jackson` | Jackson module for JSON-encoded logs. |
| `log-guard-benchmarks` | JMH harness. Never published, `-Pbench` only. |

Core is Spring-free and dependency-free, and stays that way. A privacy library with a transitive
dependency tree is a harder sell to the team that has to approve it.

## Building

```bash
./mvnw test        # unit + Testcontainers integration tests (Docker required)
./mvnw package     # module jars
```

Running the demo application is covered under [Try it](#try-it).

Architecture record: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
Release process: [`docs/RELEASING.md`](docs/RELEASING.md).

## Licence

Apache-2.0
