# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Ten fixes found by a review of the modules, three of them leaks. All of it lands before the first
artifact is published, so no released version carries these defects.

### Security

- **A type reached at exhausted scan depth was permanently marked as visited**, so every later,
  shallower path to it reported no personal data. A class whose only route to an annotated field ran
  through that type was judged clean and printed by its own `toString`.
- **The startup validator read declared fields only.** With the usual `@MappedSuperclass` layout it
  reported nothing for an inherited unannotated field that Lombok's `toString` prints, and an
  inherited `@Pii(strategy = HASH)` never raised `MissingHashSaltException` — leaving those values
  rendered `***` instead of the stable digest they were configured for.
- **Message truncation cut mid-token**, and half an address matches no pattern, so it was emitted
  raw. Reachable by padding a field until the personal data straddles the cap. The cut now moves
  back to the last separator; with none in reach the cap stands.
- **`@Pii` on a getter masked nothing** while appearing to work — see Removed.

### Removed

- **`ElementType.METHOD` from `@Pii`.** The target compiled but nothing ever scanned methods, so an
  annotated getter left the object to its raw `toString`. Annotating a getter is now a compile
  error, which is the visible outcome; reading getter annotations would move user code onto the
  logging path and belongs in a release that designs for it.

  Source-breaking against 0.1.0 as it stands in this repository — code annotating a getter no longer
  compiles, and the fix is to move the annotation to the field. Nothing is published yet, so no
  consumer is affected.

### Fixed

- **`NullPointerException` while exporting a throwable.** Suppressed proxies refused past the depth
  limit were written into the array as nulls, and `Throwable.addSuppressed` rejects null. Needed a
  cause chain reaching the limit with a suppressed exception at the boundary, and something in the
  chain that actually masked.
- **Arguments disappeared from printf-style messages on Log4j2.** Masked parameters were re-formatted
  through `ParameterizedMessage.format` whatever the message type, so
  `logger.printf(INFO, "customer=%s", customer)` reached the appenders as `customer=%s`.
- **A property carrying both `@Pii` and `@JsonSerialize` failed serialization** rather than being
  masked; Jackson refuses to overwrite a serializer a property already has. Masking now wins the tie.
- **`FailureMode` had no effect on Log4j2.** With no failure handling at the rewrite point, any
  exception from masking escaped and Log4j2 dropped the event whatever `PLACEHOLDER`, `DROP` or
  `PASSTHROUGH` said.
- **A masking failure on the throwable channel was silent**, ignoring `PASSTHROUGH` and reaching
  Logback's status log through no reporter — the only channel that failed invisibly.
- **An invalid `log-guard.patterns.custom` regex failed startup anonymously**, with a
  `PatternSyntaxException` whose offset pointed into the combined alternation rather than at the
  property. The failure now names the pattern.

## [0.1.0] — 2026-09-01

First release. Masks personal data at the logging-event level, so console, file and OTLP exports
all see the same redacted output.

### Added

- **Masking engine** (`log-guard-core`, zero dependencies) — `@Pii` with `REDACT`, `PARTIAL`,
  `HASH` and `DROP`, a `ClassValue`-backed metadata cache, and a pattern layer over the formatted
  message with built-in patterns for email, IBAN, credit-card (Luhn-checked), E.164 phone numbers
  and Kenyan national IDs.
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
  whose name is in the PII taxonomy carrying no `@Pii`. `warn` by default, `fail` for CI, `off` to
  skip. The same scan rejects `@Pii(strategy = HASH)` when no `hash-salt` is set.
- **Log4j2 adapter** (`log-guard-log4j2`) — a `RewritePolicy` plugin covering message, context map
  and thrown chain.
- **Jackson module** (`log-guard-jackson`) — honours `@Pii` during serialization. Opt-in, on a
  mapper you build for the purpose.
- **Regex safety** — `log-guard.patterns.max-message-length` (default 8192) fails closed by masking
  the head and dropping the unexamined tail, possessive quantifiers on the numeric patterns, and a
  prefilter that decides from one counting pass whether any enabled pattern could match at all.
- **Benchmarks** (`log-guard-benchmarks`, `-Pbench`) — JMH harness; the numbers are in the README.

### Known limitations

- Anything logged before the starter installs — Boot's banner and its own first startup lines — is
  outside reach.
- On Log4j2, SLF4J key-value pairs holding objects are stringified by `log4j-slf4j2-impl` before any
  policy runs, so only the pattern layer applies to them.
- A bare name logged as a plain string is undetectable, by either layer.

[Unreleased]: https://github.com/Dancan254/log-guard/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Dancan254/log-guard/releases/tag/v0.1.0
