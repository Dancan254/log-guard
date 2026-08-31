# log-guard

## What this is
A Spring Boot starter that masks personal data at the Logback **event** level, so console,
file and OTLP exports all see the same redacted output.

Full architecture record: `docs/ARCHITECTURE.md`
(published: https://claude.ai/code/artifact/b26775b2-230b-4d55-9d63-4b8f4422440c)

Implementation plan: `docs/IMPLEMENTATION-PLAN.md`
(published: https://claude.ai/code/artifact/9a6e35cc-5bb4-4258-b3aa-cb3cea4cf882)

## What it is NOT
- Not a static scanner. It changes what reaches the appender at runtime; it does not read your code.
- Not an application. The three published modules have no web layer and no datasource. The
  `log-guard-demo` module is the exception and it never ships — it exists so the library can be
  run and watched, and it is excluded from deploy.
- Not a compliance guarantee. A bare name logged as a plain string is undetectable.

## Module layout
```
log-guard/
├── log-guard-core/                  # annotations + masking engine, zero dependencies
├── log-guard-logback/               # appender wrapping, logback-classic provided
├── log-guard-spring-boot-starter/   # auto-config, properties, startup validator
└── log-guard-demo/                  # runnable Boot app + OTel collector, never deployed
```

Core must stay Spring-free and dependency-free. That constraint is the point, not an accident —
a privacy library with transitive dependencies is a harder sell to the team that approves it.

## Central design decision
Mask the `ILoggingEvent`, never the pattern layout. The OpenTelemetry Logback appender reads
`event.getFormattedMessage()` directly, so layout-level masking would redact the console and
still export raw PII over OTLP.

The masking wrapper sits **above** the async boundary, because Logback will not let anything
replace an AsyncAppender's single child:
`Logger → MaskingWrapper → AsyncAppender → Console / OTLP`

Masking still runs on the async worker rather than the request thread, because the wrapped event
masks lazily and does not mask in `prepareForDeferredProcessing()`.

## Two masking layers
- **Type-aware** — reads cached `@Pii` metadata per class, renders a masked `toString()`.
  Precise, no false positives, only sees your own objects.
- **Pattern** — regex over the formatted message behind a cheap prefilter. Catches Hibernate
  bind parameters and third-party output, cannot catch unstructured PII.

Both are needed. Neither is sufficient.

## Five channels masked per event
`getArgumentArray()` · `getFormattedMessage()` · `getMDCPropertyMap()` · `getKeyValuePairs()` · `IThrowableProxy`

The throwable proxy extends Logback's `ThrowableProxy` because the OpenTelemetry Logback appender
exports an exception only when it can pull a `Throwable` out of one. It hands over a masked
stand-in; an exception whose chain held nothing to mask is passed through untouched, so
`exception.type` stays exact except where masking actually changed something.

The same appender only inspects a logger's **top-level** appenders, so it never finds one log-guard
has wrapped. Hand it the SDK through `MaskingAppenderWrapper.getDelegate()` —
`log-guard-demo`'s `DemoApplication` shows the four lines.

## Nesting
Nested objects, arrays, collections and maps are rendered recursively: depth 3, 10 elements, an
identity cycle guard, and reflection only into classes that carry `@Pii` (narrow it further with
`log-guard.nesting.base-packages`). A class with no annotation of its own is still rendered when one
of its **declared field types** carries `@Pii` — a field typed `Object` hides whatever it holds.

## Startup validator
Scans `@Entity` classes in the app's own packages (Spring's metadata reader, matched on the
annotation *name*, so the starter needs no JPA dependency) and reports a class that declares a
`toString` and holds a field whose name is in the PII taxonomy carrying no `@Pii`.
`warn` by default, `fail` for CI, `off` to skip. The same scan throws `MissingHashSaltException`
when any `@Pii(strategy = HASH)` is found with a blank salt.

Lombok's `@ToString.Exclude` is source-retained and invisible at runtime, so it cannot silence a
finding — `@Pii(strategy = DROP)` is the opt-out.

## Regex safety
`log-guard.patterns.max-message-length` (default 8192) caps what the pattern layer scans. Past the
cap the head is masked and the tail is replaced with `…[log-guard: message truncated]` — it fails
closed, because skipping the regex on long input is a leak anyone can trigger by padding a field.
The prefilter is a flat ASCII table, so a line with no trigger character never reaches the regex.

Benchmarks live in `log-guard-benchmarks` (JMH), outside the normal build:
`./mvnw -Pbench -pl log-guard-benchmarks verify`.

## Versions
- Spring Boot 4.1.1, Java 25
- Testcontainers version comes from the Boot BOM — never pin it by hand
- Container images pinned: `postgres:18-alpine`

## How to build
```
./mvnw test        # unit tests + Testcontainers integration tests (Docker required)
./mvnw package     # builds the module jars
./mvnw -pl log-guard-demo spring-boot:run   # runs the demo app (starts Postgres + OTel collector)
```

## How Claude works in this repo

Claude writes the implementation, one phase at a time, following `docs/IMPLEMENTATION-PLAN.md`.
Each phase ends at a manual checkpoint that the maintainer runs before the next phase starts.
Decisions are stated in the plan before the code is written — if a decision is not in the plan,
ask before coding it.

## Build order
- **v0.1** — core engine, `@Pii`, Logback wrapper, auto-config, built-in patterns
- **v0.2** — MDC, key-value pairs, throwable masking, nesting, startup validator
- **v0.3** — Log4j2 `RewritePolicy`, JMH benchmarks, Jackson module, Maven Central publishing
