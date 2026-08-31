# log-guard — implementation plan (v0.1 → v0.3)

> **All eight phases are delivered and merged** (PRs #1–#7, 2026-08-30 → 2026-09-01). The plan is
> kept as the record of what was decided before the code was written. Where the build disagreed
> with the plan, the plan has been corrected in place and the disagreement is listed under
> [What the build changed](#what-the-build-changed).

Claude writes the implementation. You review each phase and run the manual checkpoint at the end
of it. Nothing moves to the next phase until you say the checkpoint passed.

Supersedes the hand-written learning plan. Architecture record: `docs/ARCHITECTURE.md`.

---

## How we work

- **One phase, one branch, one PR.** Branch name given per phase. I never commit or push — I hand
  you the message, you commit.
- **I state decisions, not options.** Every non-obvious choice below is already made, with the
  reasoning. Disagree on any of them before the phase starts, not after.
- **Zero new dependencies in `log-guard-core`, `-logback`, `-spring-boot-starter`.** That rule is
  the product. `log-guard-demo` and `log-guard-benchmarks` are exempt — they never ship.
- **Every phase ends green.** `./mvnw test` passes before I hand it back.
- Your checkpoint is written as literal commands. If a checkpoint needs no manual run, it says so.

---

## Phase map

| # | Phase | Ships | Your checkpoint |
|---|---|---|---|
| 1 | Core masking engine | v0.1 | `./mvnw -pl log-guard-core test` + read printed before/after |
| 2 | Logback event wrapping | v0.1 | `./mvnw -pl log-guard-logback test` |
| 3 | Spring wiring + demo app | **v0.1** | **Run the app, curl it, watch console + OTel collector** |
| 4 | Remaining three channels + nesting | v0.2 | Demo endpoints per channel |
| 5 | Startup validator + salt fail-fast | v0.2 | Boot the demo in `warn`, then `fail` |
| 6 | Performance and regex safety | v0.2 | Read the JMH numbers |
| 7 | Log4j2 + Jackson | v0.3 | Swap the demo to Log4j2, same output |
| 8 | Release engineering | v0.3 | Consume `0.1.0` from your local repo in a fresh app |

---

# Phase 1 — the core masking engine

`feat/core-masking-engine` · `log-guard-core` only · no Spring, no Logback, no dependencies.

## What I build

```
io.github.dancan254.logguard
├── Pii, MaskStrategy, PiiCategory, BuiltInPattern      (exist)
├── mask/ValueMasker                                     value + strategy -> masked string
├── meta/PiiMetadata                                     record: List<PiiField>, boolean hasPii
├── meta/PiiField                                        record: name, Field, strategy, category
├── meta/PiiMetadataCache                                ClassValue<PiiMetadata>
├── render/ObjectRenderer                                object -> masked toString
├── pattern/PatternMasker                                formatted string -> masked string
├── pattern/Luhn                                         package-private check digit
├── MaskingConfig                                        record, the engine's whole input
└── LogGuardMasker                                       the one entry point
```

## Decisions already made

**`PARTIAL` is shape-driven, not category-driven.** Category is reporting metadata in v0.1, it does
not change behaviour. The rules, in order:

| Input shape | Output | Example |
|---|---|---|
| contains `@` and matches an email shape | first char of local part + full domain | `j****@acme.io` |
| length ≥ 12 | first 5 + `****` + last 3 | `+2547****891` |
| length ≥ 8 | `****` + last 3 | `****891` |
| shorter | degrades to `REDACT` | `***` |

The mask block is a **fixed four asterisks**, never `repeat(n)`. A length-proportional mask leaks
the length of the secret, which for a national ID or a card is most of what you needed to know.

**`DROP` never reaches `ValueMasker`.** It is a field-level decision, not a value transformation —
`ObjectRenderer` omits the field and the masker never sees it. This kills the sentinel/`Optional`
question the old plan raised: there is nothing to represent.

**`HASH` is `#` + first 6 hex of `SHA-256(salt || value)`.** Salt is a constructor argument, never a
static. Six hex is 16.7M buckets: enough to correlate one user across a trace, not enough to be a
stable global identifier — that tradeoff is deliberate and goes in the README.

**Blank salt + `HASH` at log time degrades to `REDACT`.** It does not throw. An exception inside an
appender loses the log line, and a config mistake must never cost you the incident you were
debugging. Startup is where that fails loudly (Phase 3 and 5).

**`ObjectRenderer` builds the string itself; it does not rewrite `toString()`.** Rewriting means
regex-ing Lombok's output, needing the raw values to find them, and clobbering every field that
shares a value. Failure mode of building it ourselves is cosmetic; failure mode of rewriting is a
leak. Output shape mirrors Lombok — `Customer(id=42, email=***)` — so nothing looks foreign.

**Every field read is guarded.** `setAccessible` failure, a lazy JPA association throwing outside a
transaction, an exploding getter — all render as `<unreadable>` and never propagate. A privacy
library that can throw from inside a log statement is not deployable.

**No recursion in v0.1.** Top-level annotated fields only. Nested objects, collections, cycles and
depth limits are Phase 4, together.

**Class with no `@Pii` at all returns `toString()` unchanged, allocating nothing.** This is the
overwhelmingly common case and it is a boolean check on cached metadata.

**Metadata is cached in `ClassValue`, not a `ConcurrentHashMap`.** A `Map<Class<?>, ?>` held by a
library is a classloader leak in any container that redeploys.

**Patterns compile to one named alternation, matched in a single pass.** Overlap is impossible by
construction — leftmost-first wins. The prefilter is one character scan: enabled patterns declare
whether they need `@` or a digit, and a line with neither skips the regex entirely. Adding a
pattern forces you to re-declare its prefilter requirement, so the prefilter cannot silently
produce a false negative.

**`CREDIT_CARD` gets a Luhn check.** Without it the rule redacts order IDs, correlation IDs and
concatenated timestamps, and the first time it eats a support engineer's order number the library
gets switched off.

**Pattern regexes exclude `*` and `#` from their character classes.** This is what stops the two
layers double-masking: `j****@acme.io` produced by the type layer does not re-match the email
pattern, and `#a3f91c` hashed again would not be `#a3f91c`. Ordering is fixed — type-aware first,
patterns second, over the type-aware output.

**No nested quantifiers, every repetition bounded.** Log messages are attacker-influenced; a
catastrophic-backtracking bug here is a denial of service, not a leak. A test feeds each pattern a
10k-character adversarial string and asserts it completes.

## Tests

```
ValueMaskerTest
  should_redact_fully_when_strategy_is_redact
  should_keep_domain_when_partial_masking_an_email
  should_keep_head_and_tail_when_partial_masking_a_long_value
  should_degrade_to_redact_when_value_is_too_short_for_partial
  should_use_fixed_width_mask_when_partial_so_length_does_not_leak
  should_produce_stable_digest_when_same_value_and_salt
  should_produce_different_digest_when_salt_differs
  should_degrade_to_redact_when_hash_is_used_without_salt

PiiMetadataCacheTest
  should_find_annotated_fields_when_class_is_scanned
  should_include_inherited_fields_when_superclass_is_annotated
  should_ignore_static_and_synthetic_fields_when_scanning
  should_read_annotation_when_type_is_a_record
  should_return_same_instance_when_scanned_twice
  should_report_no_pii_when_class_has_no_annotations

ObjectRendererTest
  should_mask_annotated_field_when_rendering
  should_leave_unannotated_field_visible_when_rendering
  should_render_null_field_as_null
  should_return_original_to_string_when_class_has_no_annotations
  should_omit_field_entirely_when_strategy_is_drop
  should_render_placeholder_when_field_read_throws

PatternMaskerTest
  should_mask_email_when_present_in_message
  should_mask_every_occurrence_when_message_has_several
  should_leave_message_unchanged_when_nothing_matches
  should_skip_regex_when_prefilter_finds_no_trigger_character
  should_not_mask_order_id_when_luhn_check_fails
  should_not_remask_output_produced_by_the_type_layer
  should_complete_within_a_second_when_input_is_adversarial

LogGuardMaskerTest
  should_apply_type_layer_then_pattern_layer_when_both_enabled
  should_skip_type_layer_when_disabled
```

## Your checkpoint

```bash
./mvnw -pl log-guard-core test
grep -rE "org\.springframework|ch\.qos\.logback" log-guard-core/src   # expect nothing
```

`MaskingShowcaseTest` prints a real before/after block to stdout. Read it — that block becomes the
README example, so if it looks wrong, it is wrong.

---

# Phase 2 — Logback event wrapping

`feat/logback-event-wrapping` · `log-guard-logback` · logback-classic `provided`.

## What I build

```
io.github.dancan254.logguard.logback
├── MaskingLoggingEvent      implements ILoggingEvent, delegates, masks two channels
├── MaskingAppenderWrapper   Appender<ILoggingEvent> holding a delegate
└── MaskingInstaller         walks LoggerContext, wraps, idempotently
```

## Decisions already made

**The formatted message is re-derived from the raw pattern, not patched.** `AsyncAppender` calls
`prepareForDeferredProcessing()` before queueing, which forces `getFormattedMessage()` — so by the
time our wrapper runs (deliberately *inside* the async boundary) the raw message is already
computed and cached on the event. We therefore ignore that cached value entirely and re-format
`getMessage()` against the **masked** argument array via `org.slf4j.helpers.MessageFormatter`, then
run the pattern layer over the result. Anything else masks a string that was already built from raw
PII.

**v0.1 masks two channels only:** `getArgumentArray()` and `getFormattedMessage()`. MDC, key-value
pairs and throwables are Phase 4. Two channels that work beat five that half-work.

**Masking is lazy and computed once.** The masked args and message are computed on first call and
cached on the wrapper. Appenders that never read the message pay nothing.

**Every `ILoggingEvent` method is delegated.** Timestamp, thread name, logger name, level, markers,
caller data, sequence number, LoggerContextVO. A forgotten delegate is a silent bug in somebody
else's appender, so the test asserts delegation field by field.

**On masking failure the event is emitted with its message replaced**, not dropped and not passed
through raw. You keep the fact that a log line happened, at its real level, on its real logger,
with the payload removed and one throttled `addWarn` on the Logback status manager. Dropping loses
your incident; passing through is the leak the library exists to prevent. Phase 4 makes this
configurable (`on-failure: placeholder | drop | passthrough`) with `placeholder` as the default.

**The installer wraps the appenders an `AsyncAppender` references, never the async appender
itself.** Wrapping the async appender puts regex work back on the request thread, which defeats the
whole ordering in the architecture doc.

**Idempotent by identity.** Re-running the installer skips anything already a
`MaskingAppenderWrapper`. Spring reconfigures Logback more than once during startup, and wrapping
a wrapper is double-masking.

**Detach-then-attach happens against a snapshot of the appender list**, never while iterating it.

## Tests

```
MaskingLoggingEventTest
  should_mask_annotated_argument_when_event_is_wrapped
  should_reformat_message_from_masked_arguments_when_message_was_already_cached
  should_delegate_logger_name_and_level_unchanged_when_wrapping
  should_compute_masked_message_once_when_called_repeatedly
  should_not_double_mask_when_both_layers_are_enabled

MaskingAppenderWrapperTest
  should_pass_masked_event_to_delegate_when_appending
  should_start_delegate_when_wrapper_starts
  should_emit_placeholder_message_when_masking_fails
  should_keep_level_and_logger_when_masking_fails

MaskingInstallerTest
  should_wrap_every_attached_appender_when_applied
  should_not_wrap_twice_when_applied_repeatedly
  should_wrap_referenced_appenders_when_async_appender_is_present
```

## Your checkpoint

```bash
./mvnw -pl log-guard-core,log-guard-logback test
```

No app to run yet. If you want to eyeball it, `MaskingInstallerTest` has a case that logs through a
real console appender and the captured output is in the surefire report.

---

# Phase 3 — Spring wiring and the demo app

`feat/spring-autoconfig-and-demo` · the phase that makes v0.1 real. **This is your first proper
manual test.**

## What I build — the starter

- `LogGuardAutoConfiguration` gains behaviour: maps `LogGuardProperties` → `MaskingConfig`, builds
  the `LogGuardMasker`, runs `MaskingInstaller`.
- `LogGuardLoggingListener` — an `ApplicationListener` registered in `META-INF/spring.factories`,
  ordered immediately after Boot's `LoggingApplicationListener`, so masking is installed as early
  as Boot allows rather than when beans are created.
- A `LoggerContextListener` that re-installs on `onReset`, because Boot resets the context and
  a `logback-spring.xml` reload would otherwise silently unwrap everything.
- `additional-spring-configuration-metadata.json` so every property has a description and IDE
  completion.

## Decisions already made

**Two hooks, not one.** The early `ApplicationListener` gets masking installed before application
code logs anything; the `LoggerContextListener` survives every later reconfiguration. A plain
`@Bean` + `InitializingBean` is far too late — the banner, environment post-processing and every
Boot startup line have already been written.

**The gap is documented, not hidden.** Anything logged before Boot's logging system is initialised
is unmasked. The README states which lines those are instead of claiming zero.

**`log-guard.enabled: false` costs nothing.** No scan, no bean, no listener work — the
`@ConditionalOnProperty` already there is checked in the listener too, since listeners run before
conditions apply.

**Fail-fast on the salt, in the half we can know at startup.** If a custom pattern declares
`HASH` and `hash-salt` is blank, `MissingHashSaltException` at startup. `@Pii(strategy = HASH)` on a
class we have not loaded yet cannot be seen without a classpath scan — that half arrives in Phase 5
with the validator, and at log time it degrades to `REDACT` (Phase 1).

## What I build — `log-guard-demo` (new module)

A real Boot 4 app, aggregated in the root pom, `maven.deploy.skip=true`. It exists to be run.

```
log-guard-demo/
├── compose.yaml                     postgres:18-alpine + otel collector, pinned, healthchecked
├── otel-collector-config.yaml       OTLP in -> debug exporter, verbosity: detailed
└── src/main/
    ├── java/.../demo/
    │   ├── DemoApplication.java
    │   └── customer/
    │       ├── Customer.java            @Entity @Data, @Pii on email/phone/nationalId/dateOfBirth
    │       ├── CustomerRepository.java  ListCrudRepository
    │       ├── CustomerService.java
    │       ├── CustomerController.java
    │       └── dto/{CreateCustomerRequest,CustomerResponse}.java   records, validated
    └── resources/
        ├── application.yml           hibernate bind TRACE, log-guard config, OTLP endpoint
        └── db/migration/V1__customer.sql
```

New dependencies, demo module only: `spring-boot-starter-web`, `-data-jpa`, `-validation`,
`-opentelemetry`, `spring-boot-docker-compose`, `flyway-core`, `postgresql`, Lombok. Lombok is
there on purpose — the leak this library exists to close is Lombok's generated `toString()`, so the
demo has to have it.

`spring-boot-docker-compose` means `spring-boot:run` starts Postgres and the collector for you. No
separate `docker compose up` step.

## Your checkpoint

```bash
./mvnw install -DskipTests
./mvnw -pl log-guard-demo spring-boot:run
```

Then, in another terminal:

```bash
curl -s -X POST localhost:8080/customers -H 'content-type: application/json' \
  -d '{"email":"jane.wanjiru@acme.io","phoneNumber":"+254712345891","nationalId":"31234567","dateOfBirth":"1994-03-11"}'

curl -s localhost:8080/customers/by-email?email=jane.wanjiru@acme.io
```

**What to check, in order:**

1. The app console shows `Processing customer Customer(id=1, email=#a3f91c, phoneNumber=+2547****891, …)`.
2. The Hibernate bind-parameter line at `TRACE` shows the masked address, not `jane.wanjiru@acme.io`.
3. `docker compose -f log-guard-demo/compose.yaml logs otel-collector` shows the **same masked
   text**. This is the claim the whole architecture rests on — layout masking would have shown you
   a clean console and shipped raw PII here.
4. Set `log-guard.enabled: false`, restart, and confirm the PII comes back. If it does not, the
   library is not doing what you think it is doing.

**v0.1 is done when all four hold** and `./mvnw test` is green from a clean clone.

---

# Phase 4 — the remaining three channels, and nesting

`feat/remaining-channels` · v0.2. Two independent pieces, one branch, reviewed separately.

## Channels

- **MDC** — pattern layer over every value, plus a configurable key list
  (`log-guard.mdc.redact-keys: [email, phone, ssn]`) forcing `REDACT` regardless of content.
  `MDC.put("email", …)` leaks into every subsequent line on that thread, which makes it the worst
  of the five.
- **Key-value pairs** — SLF4J 2 structured logging. Object values go through the type layer, string
  values through the pattern layer.
- **`IThrowableProxy`** — a wrapping proxy that masks the message across the full cause chain and
  suppressed array, with a depth cap and identity-based cycle detection because a self-referencing
  cause chain is legal and does happen.

## Nesting

Recursion into nested objects, arrays, `Collection`, `Map`. Depth limit (default 3, configurable),
`IdentityHashMap` cycle guard, element cap (`first 10, then …(+N more)`), and a rule that any type
outside your configured base package is rendered by its own `toString()` and handed to the pattern
layer rather than reflected into. Reflecting into arbitrary third-party objects is how a logging
library ends up triggering a lazy load or a database round trip.

Also lands: `log-guard.on-failure: placeholder | drop | passthrough`.

## Your checkpoint

Demo gains one endpoint per channel — `/leaks/mdc`, `/leaks/kv`, `/leaks/exception`,
`/leaks/nested`. Hit each, confirm the console and the collector agree, confirm the exception one
masks the cause chain and not just the top frame.

---

# Phase 5 — the startup validator

`feat/startup-validator` · v0.2. The part that fixes the Lombok problem at its source rather than
containing it.

Scans `@Entity` classes in the app's base package using Spring's existing classpath scanner (no new
dependency). Reports a class that has a generated `toString()` and a field whose **name** matches
the PII taxonomy — `email`, `phone`, `msisdn`, `ssn`, `nationalId`, `dob`, `dateOfBirth`, `iban`,
`pan`, `cardNumber`, `password`, `token`, `secret` — carrying no `@Pii`.

`@ToString.Exclude` cannot be part of that test, though the plan originally said it would be:
Lombok's annotations are `SOURCE`-retained and exist in neither the class file nor the runtime
model, so nothing can see them. `@Pii(strategy = DROP)` is the opt-out instead.

`warn` is the default: failing someone's app on a dependency upgrade is hostile. `fail` is
documented for `application-test.yml` so CI catches new entities. `off` skips the scan entirely.

The same scan closes the salt gap: any `@Pii(strategy = HASH)` found with a blank `hash-salt` throws
`MissingHashSaltException` at startup.

## Your checkpoint

The demo grows a deliberately unannotated `LegacyCustomer` entity. Boot it — one WARN block naming
the class and fields. Set `unannotated-entity: fail`, boot again — context refuses to start with a
message that tells you exactly what to annotate. Set `off` — silent.

---

# Phase 6 — performance and regex safety

`feat/performance` · v0.2. New module `log-guard-benchmarks`, JMH, excluded from the normal build
and from deploy.

Benchmarks: the no-PII fast path, a type-aware render, pattern-only over a typical line, the full
pipeline, and a 100-field entity. Published in the README as numbers, not adjectives.

Safety work in the same phase:

- **A message-length cap** (`log-guard.patterns.max-message-length`, default 8192). Above it we mask
  the first N characters and replace the tail with `…[log-guard: message truncated]`. Fails closed:
  the alternative — skipping the regex on long input — is a leak any attacker can trigger by
  padding a field.
- Adversarial-input suite per pattern, asserting bounded time.
- Allocation work: reused `StringBuilder`, prefilter as a bitset over the trigger characters,
  `CharSequence` paths where they avoid a copy.

## Your checkpoint

```bash
./mvnw -pl log-guard-benchmarks verify -Pbench
```

Read the table. The number that matters is the no-PII fast path — if a line with nothing to mask
costs more than a few hundred nanoseconds, the library will get switched off in production and
nothing else in this list matters.

---

# Phase 7 — Log4j2 and Jackson

`feat/log4j2-and-jackson` · v0.3. Two new modules, both optional at runtime.

- **`log-guard-log4j2`** — a `RewritePolicy` plugin masking `Message`, `ContextMap` and the thrown
  proxy. Log4j2's rewrite point is a genuinely different shape from Logback's appender wrapping;
  the engine is shared, the adapter is not.
- **`log-guard-jackson`** — a Jackson `Module` honouring `@Pii` during serialization, for
  JSON-encoded logs (logstash encoder) and for anyone who wants it on an outbound payload.
  **Opt-in only, registered on its own `ObjectMapper`.** Auto-registering into the app's primary
  mapper would silently change REST responses, which is a spectacular way to break someone's API
  from a logging library.

## Your checkpoint

Swap the demo's `spring-boot-starter-logging` for `-log4j2`, run the same curls, confirm identical
masked output. That equivalence is the whole point of the module.

---

# Phase 8 — release engineering

`chore/maven-central-release` · v0.3.

Sources and javadoc jars, GPG signing, `central-publishing-maven-plugin`, reproducible-build
timestamps, `module-info.java` for core so it is a proper JPMS module, `maven.deploy.skip` on demo
and benchmarks, a CHANGELOG, a release workflow gated on a green CI run, and a README rewritten
with **output pasted from a real run**, not hand-typed. CI grows a job that consumes the published
artifact from a clean app to prove the starter auto-configures with no other configuration.

## Your checkpoint

```bash
./mvnw install
```

then add `io.github.dancan254:log-guard-spring-boot-starter:0.1.0` to a brand-new Boot app,
`log.info("{}", someEntity)`, and confirm it masks with no configuration beyond the dependency.

---

## What the build changed

Six places where reality disagreed with a decision written above. Each was found by running the
thing, not by reading it.

| Phase | The plan said | What is true |
|---|---|---|
| 2 | Wrap inside the async appender | `AsyncAppenderBase.detachAppender()` never decrements `appenderCount`, so nothing can replace its single child. The wrapper sits **above** the boundary and masks lazily, which keeps the work on the async worker anyway. |
| 3 | The OTel appender would just work | `OpenTelemetryAppender.install()` inspects only top-level appenders and never finds a wrapped one, so it exports nothing. The application hands it the SDK through `MaskingAppenderWrapper.getDelegate()`. |
| 4 | A wrapping `IThrowableProxy` is enough | The OTel appender exports an exception only through a real `ThrowableProxy` it can read a `Throwable` off. The masking proxy extends one and hands over a masked stand-in; an unchanged chain is passed through so `exception.type` stays exact. |
| 5 | `@ToString.Exclude` silences a finding | It is `SOURCE`-retained and invisible at runtime. `@Pii(strategy = DROP)` is the opt-out. |
| 6 | Benchmarks would document the engine | They condemned it: a line with nothing to mask cost **14 µs**, because the prefilter only asked "is there a digit?" and the email branch was quadratic in line length. Both fixed; that line now costs 126 ns. |
| 7–8 | Making both adapters optional is tidy | It broke a clean application: Logback present, adapter absent, startup dead. The Logback adapter ships by default; `logback-classic` stays optional. Caught by the clean-app job, which no unit test would have. |

---

## Things deliberately not in this plan

- A `%mask` conversion word. It is the thing the architecture rejects.
- Masking `System.out`. Out of scope, and pretending otherwise oversells the library.
- Encrypting rather than hashing. Reversible masking means key management, and key management means
  this is no longer a zero-dependency library.
- Config hot-reload. Restart is fine for a masking policy and the concurrency cost is not.
