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
- Not an application. There is no web layer, no datasource, no Docker image. Postgres appears in
  test scope only, for the Hibernate bind-parameter test.
- Not a compliance guarantee. A bare name logged as a plain string is undetectable.

## Module layout
```
log-guard/
├── log-guard-core/                  # annotations + masking engine, zero dependencies
├── log-guard-logback/               # appender wrapping, logback-classic provided
└── log-guard-spring-boot-starter/   # auto-config, properties, startup validator
```

Core must stay Spring-free and dependency-free. That constraint is the point, not an accident —
a privacy library with transitive dependencies is a harder sell to the team that approves it.

## Central design decision
Mask the `ILoggingEvent`, never the pattern layout. The OpenTelemetry Logback appender reads
`event.getFormattedMessage()` directly, so layout-level masking would redact the console and
still export raw PII over OTLP.

The masking wrapper sits **inside** the async boundary:
`Logger → AsyncAppender → MaskingWrapper → Console / OTLP`

## Two masking layers
- **Type-aware** — reads cached `@Pii` metadata per class, renders a masked `toString()`.
  Precise, no false positives, only sees your own objects.
- **Pattern** — regex over the formatted message behind a cheap prefilter. Catches Hibernate
  bind parameters and third-party output, cannot catch unstructured PII.

Both are needed. Neither is sufficient.

## Five channels masked per event
`getArgumentArray()` · `getFormattedMessage()` · `getMDCPropertyMap()` · `getKeyValuePairs()` · `IThrowableProxy`

## Versions
- Spring Boot 4.1.1, Java 25
- Testcontainers version comes from the Boot BOM — never pin it by hand
- Container images pinned: `postgres:18-alpine`

## How to build
```
./mvnw test        # unit tests + Testcontainers integration tests (Docker required)
./mvnw package     # builds all three module jars
```

## How Claude works in this repo

The implementation is hand-written. Do not generate code in `src/main/java` — ask first.

Useful for: explaining how a Logback or Spring internal behaves, reviewing code already written
(`/code-review`, or the `spring-boot-reviewer` agent), and plumbing not being learned deliberately
(poms, CI, docs, Central publishing).

## Build order
- **v0.1** — core engine, `@Pii`, Logback wrapper, auto-config, built-in patterns
- **v0.2** — MDC, key-value pairs, throwable masking, startup validator
- **v0.3** — Log4j2 `RewritePolicy`, JMH benchmarks, Jackson module, Maven Central publishing
