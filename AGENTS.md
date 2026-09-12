# AGENTS.md

Instructions for AI coding agents working in this repository. Humans want
[CONTRIBUTING.md](CONTRIBUTING.md); everything there applies here too.

## What this project is

A Spring Boot starter that masks personal data at the Logback **event** level, so console, file and
OTLP exports all see the same redacted output. It is a library: no web layer, no datasource, except
in `log-guard-demo`, which is runnable and never published.

## Work in phases, and stop at the checkpoint

Implementation follows [`docs/IMPLEMENTATION-PLAN.md`](docs/IMPLEMENTATION-PLAN.md), one phase at a
time. Each phase ends at a checkpoint the maintainer runs before the next phase starts. **If a
decision is not already in the plan, ask before coding it.** Do not run ahead to the next phase
because the current one went well.

State the approach before writing code for anything non-trivial, then wait for confirmation.

## Ask before you do any of these

- **Adding a dependency.** Name it and say why it beats the alternatives. Adding one to
  `log-guard-core` is very likely the wrong answer — see below.
- **Creating an abstraction**: a new interface, base class, helper or utility.
- **Adding a configuration property or changing `@Pii`.** Both are public API and permanent.
- **Touching the event wrapper or the async boundary.**

Never implement beyond the task. A bug fix is a bug fix: no drive-by refactors, no "while I'm
here". Flag anything else you spot instead of fixing it.

## Invariants that must survive your change

**`log-guard-core` has zero dependencies.** Not Spring, not Logback, not Jackson, not a utility
library. This is the constraint that makes the library approvable inside a company. Adapter code
goes in `log-guard-logback`, `log-guard-log4j2`, `log-guard-jackson` or the starter.

**Masking happens on the `ILoggingEvent`, never in a layout or an encoder.** The OpenTelemetry
Logback appender reads `event.getFormattedMessage()` directly, so layout masking would redact the
console and still ship raw personal data over OTLP. This is the whole design.

**The wrapper sits above the async boundary** (`Logger → MaskingWrapper → AsyncAppender →
Console / OTLP`), because Logback will not let anything replace an `AsyncAppender`'s single child.
Masking is still lazy, so the work lands on the async worker rather than the request thread. Do not
"fix" this by masking eagerly in `prepareForDeferredProcessing()`.

**Failure is closed, never open.** Unmasked output is never the fallback path.

**Five channels are masked per event**: `getArgumentArray()`, `getFormattedMessage()`,
`getMDCPropertyMap()`, `getKeyValuePairs()` and `IThrowableProxy`. A change that adds a masking
capability usually has to reach all five.

## Things that look wrong and are deliberate

Do not "clean up" any of these without being asked:

- `KENYAN_NATIONAL_ID` is off by default. A bare 7–8 digit run is also an order number and a port.
- The Jackson module is not auto-registered. Registering it on the application's primary
  `ObjectMapper` would silently mask REST responses.
- `MaskingThrowableProxy` extends Logback's `ThrowableProxy` rather than implementing the interface,
  because the OTel appender only exports an exception it can pull a real `Throwable` out of.
- An exception whose chain held nothing to mask is passed through untouched, so `exception.type`
  stays exact.
- Benchmark ceilings are ~10x the measured numbers. A performance gate that fails on shared-runner
  noise gets deleted within a month.
- `--pinentry-mode loopback` in the GPG release profile is not optional; CI has no tty.
- The truncation notice on an over-length message is a fail-closed behaviour, not a bug.

## Commands

```bash
./mvnw test                                    # unit + Testcontainers integration tests (Docker)
./mvnw package                                 # module jars
./mvnw -pl log-guard-demo spring-boot:run      # demo on 8081, starts Postgres + OTel collector
./smoke/verify.sh                              # clean Boot app consuming the starter
./mvnw -Pbench -pl log-guard-benchmarks verify # JMH, outside the normal build
```

Run `./mvnw test` before claiming a change works. If you changed masking behaviour, the demo or
`smoke/verify.sh` is the only way to see the OTLP path.

## Conventions

Java 25 and Spring Boot 4 idioms throughout: records, pattern matching, sealed types, `var` where
the type is obvious. Boot 3 test APIs do not compile here — `@MockBean` and `@SpyBean` are gone,
and `MockMvc` matchers have been replaced by `MockMvcTester`. Never mock persistence.

Tests are `*Test` for unit and `*IntegrationTest` for integration, with methods named
`should_<expected>_when_<condition>`. Adversarial masking cases go in `AdversarialInputTest`.

Comments explain why, never what. No `// TODO` in committed code.

## Git

- Conventional commits: `type(scope): short description`, imperative, lowercase, no period, max 72
  characters. A body only when the why is non-obvious. Never `wip`, `updates` or `fix stuff`.
- Branch `type/short-kebab-description` from `main`, one concern per branch.
- **Never commit or push directly to `main` on your own initiative.** Work on a branch and open a
  pull request. The maintainer may ask you to commit a documentation or release chore straight to
  `main`; that is their call to make, never yours to assume. Library code goes through a pull
  request regardless of who asks.
- **Never push, and never open a pull request, without being asked.** Committing locally is fine;
  anything that leaves the machine is the maintainer's decision.
- Never force-push, never amend or rebase a commit that has already been pushed, and never revert
  or drop someone else's commit on your own initiative.
- Run `./mvnw test` before committing. Never commit build output, `target/`, or generated poms.
- Never merge without a green CI run. Pull requests are squash-merged, so the title becomes the
  commit message on `main`.
- Never tag or publish a release on your own initiative. Releasing is a human decision documented
  in [`docs/RELEASING.md`](docs/RELEASING.md), and a published version can never be withdrawn.

## Reference

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — why the design is shaped this way.
- [`docs/IMPLEMENTATION-PLAN.md`](docs/IMPLEMENTATION-PLAN.md) — the phases and their checkpoints.
- [`docs/RELEASING.md`](docs/RELEASING.md) — the Maven Central runbook.
- [`README.md`](README.md) — the user-facing API surface.
