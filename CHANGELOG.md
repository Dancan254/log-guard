# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] — 2026-09-01

First release. Masks personal data at the logging-event level, so console, file and OTLP exports
all see the same redacted output.

### Added

- **Masking engine** (`log-guard-core`, zero dependencies) — `@Pii` on a field or record component
  with `REDACT`, `PARTIAL`, `HASH` and `DROP`, a `ClassValue`-backed metadata cache, and a pattern
  layer over the formatted message with built-in patterns for email, IBAN, credit-card
  (Luhn-checked), E.164 phone numbers and Kenyan national IDs.
- **Logback adapter** (`log-guard-logback`) — wraps every appender above the async boundary and
  masks the event lazily, so reflection and regex run on the async worker rather than the request
  thread.
- **Spring Boot starter** (`log-guard-spring-boot-starter`) — auto-configuration, `log-guard.*`
  properties, and installation before the first log line rather than after the context refreshes.
- **All five event channels** — arguments, formatted message, MDC, key-value pairs and the
  throwable chain, including causes and suppressed exceptions.
- **Nested rendering** — objects, arrays, collections and maps, with a depth limit, an element cap
  and an identity cycle guard. A class with no annotation of its own is rendered when one of its
  declared field types carries `@Pii`.
- **Startup validator** — reports `@Entity` classes that declare a `toString` and hold a field
  whose name is in the PII taxonomy carrying no `@Pii`, inherited fields included, so the usual
  `@MappedSuperclass` layout is covered. `warn` by default, `fail` for CI, `off` to skip. The same
  scan rejects `@Pii(strategy = HASH)` when no `hash-salt` is set.
- **Log4j2 adapter** (`log-guard-log4j2`) — a `RewritePolicy` plugin covering message, context map
  and thrown chain, honouring the configured `on-failure` mode when masking itself throws.
- **Jackson module** (`log-guard-jackson`) — honours `@Pii` during serialization, including on a
  property that declares its own `@JsonSerialize`. Opt-in, on a mapper you build for the purpose.
- **Regex safety** — `log-guard.patterns.max-message-length` (default 8192) fails closed by masking
  the head and dropping the unexamined tail, cutting at a token boundary so a value straddling the
  cap cannot be emitted as an unmatched fragment. Possessive quantifiers on the numeric patterns, a
  prefilter that decides from one counting pass whether any enabled pattern could match at all, and
  a custom regex that does not compile is rejected by name rather than by offset into the combined
  alternation.
- **Benchmarks** (`log-guard-benchmarks`, `-Pbench`) — JMH harness; the numbers are in the README.

### Known limitations

- Anything logged before the starter installs — Boot's banner and its own first startup lines — is
  outside reach.
- On Log4j2, SLF4J key-value pairs holding objects are stringified by `log4j-slf4j2-impl` before any
  policy runs, so only the pattern layer applies to them.
- A bare name logged as a plain string is undetectable, by either layer.

[Unreleased]: https://github.com/Dancan254/log-guard/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Dancan254/log-guard/releases/tag/v0.1.0
