# log-guard v0.1 — implementation plan

You are writing this by hand. This plan tells you **what** to build, **what "done" looks like**,
and **where the traps are**. It deliberately does not give you the code.

Work test-first. Every step ends with a green test and a commit.

---

## Ground rules

- Nothing in `log-guard-core` may import Spring or Logback. If you reach for either, the
  responsibility belongs in a different module.
- No new dependencies. Everything below is JDK or already on the classpath.
- One step, one commit, `type(scope): subject`.

---

# Phase 1 — the engine (core, no framework)

Do this whole phase without touching Logback. It is the part with the most logic and the least
framework knowledge required, so you get moving without first having to understand Logback's
internals. When it is done you will have something you already trust.

---

## Step 1 — Mask a single value

**Build.** Something that takes a `String` value plus a `MaskStrategy` and returns the masked
form. The four strategies from `MaskStrategy` are already defined.

**Decisions you have to make.**
- `PARTIAL`: how many characters do you keep, and from which end? A phone number wants the tail;
  an email arguably wants the first letter and the domain. Decide whether one rule covers both or
  whether `PARTIAL` needs to know the category.
- `DROP` is not a string transformation — it is a signal that the field should not appear at all.
  Decide how the engine represents that. A sentinel? An `Optional`? This choice leaks into Step 3,
  so make it deliberately.

**Traps.**
- A short value must not survive `PARTIAL`. `"ab"` with "keep first and last" leaks everything.
  Define a minimum length below which `PARTIAL` degrades to `REDACT`.
- `HASH` must take the salt as an input, not read it from a static. Testing a static salt is
  miserable and it makes the engine un-reusable.
- Do not truncate the digest so far that collisions become likely across a big user base. Six hex
  characters is 16.7M buckets — fine for correlating within a trace, and you should know that is
  the tradeoff you chose.

**APIs to look up.** `MessageDigest.getInstance("SHA-256")`, `HexFormat` (JDK 17+),
`String.repeat`, `StandardCharsets.UTF_8`.

**Done when.**
```
should_redact_fully_when_strategy_is_redact
should_degrade_to_redact_when_value_is_too_short_for_partial
should_produce_stable_digest_when_same_value_and_salt
should_produce_different_digest_when_salt_differs
```

---

## Step 2 — Read `@Pii` metadata off a class, once

**Build.** Given a `Class<?>`, produce a description of which fields carry `@Pii` and with which
strategy. Cache it so the reflection cost is paid once per class, ever.

**Traps.**
- Include inherited fields. `getDeclaredFields()` does not walk superclasses; JPA entities very
  often extend a mapped superclass.
- Skip `static` and `synthetic` fields. Coverage tools inject `$jacocoData`; inner classes carry
  `this$0`. Both will surprise you in tests.
- Records are not classes with fields in the way you expect. `@Pii` targets `RECORD_COMPONENT`,
  and whether the annotation is also visible on the backing field depends on the targets declared.
  Test a record explicitly — do not assume.
- `setAccessible(true)` on a private field of a user class in the unnamed module is fine. On a JDK
  class it is not. Decide what happens when you cannot read a field, and make it not throw.

**APIs to look up.** `ClassValue`, `Class.getSuperclass`, `Field.isSynthetic`,
`Modifier.isStatic`, `Class.isRecord`, `Class.getRecordComponents`.

**Why `ClassValue` and not a `ConcurrentHashMap`.** Worth understanding before you use it: it is
keyed on the class, it does not prevent the class or its loader from being unloaded, and lookup is
faster than a map. A `HashMap<Class, ...>` in a library is a classloader leak in an app server.

**Done when.**
```
should_find_annotated_fields_when_class_is_scanned
should_include_inherited_fields_when_superclass_is_annotated
should_ignore_static_and_synthetic_fields_when_scanning
should_read_annotation_when_type_is_a_record
should_return_cached_metadata_when_scanned_twice
```

---

## Step 3 — Render a masked object

**Build.** Given an object whose class has PII metadata, produce the masked string that will
replace it in the log line.

**The real decision in this step.** Two approaches:

**(a) Rewrite the existing `toString()` output.** Call `toString()`, then find and replace the
sensitive values inside it. Preserves the class's real formatting, including a hand-written
`toString()`.
*But:* you are regex-ing Lombok's output format, you need the raw values to find them, and a value
that appears twice (an email in two fields) gets clobbered in both.

**(b) Build the string yourself from the fields.** Read each field, mask the annotated ones, format
the whole thing.
*But:* the output no longer matches a custom `toString()`, so a class that formats itself specially
loses that.

**Recommendation: (b).** The failure mode of (a) is a leak; the failure mode of (b) is cosmetic. In
a privacy library, pick the option whose worst case is ugly rather than the one whose worst case is
a breach. Write down which you chose and why — that reasoning is the video.

**Scope for v0.1.** Mask only the top-level object's own annotated fields. **Do not recurse** into
nested objects. Recursion brings cycles, depth limits and collection handling, and it belongs in
v0.2.

**Traps.**
- Null fields. Render them as `null`, not as a masked value — masking a null tells the reader
  something false.
- An object with no `@Pii` fields at all: return its normal `toString()` unchanged, and do it
  without building anything. This is the common case and it must be free.
- `toString()` on a JPA entity with a lazy association will trigger a load, or throw outside a
  transaction. If you go with (b), you avoid this for annotated fields but not for unannotated
  ones. Note it; do not solve it yet.

**Done when.**
```
should_mask_annotated_field_when_rendering
should_leave_unannotated_field_visible_when_rendering
should_render_null_field_as_null
should_return_original_to_string_when_class_has_no_annotations
should_omit_field_entirely_when_strategy_is_drop
```

---

## Step 4 — Pattern masking over a formatted string

**Build.** Compile the `BuiltInPattern` set into a matcher, and mask every match found in a string.

**Traps.**
- **Catastrophic backtracking.** Keep every regex simple and anchored where you can. Nested
  quantifiers like `(\d+)+` will hang a thread on a crafted input, and log messages are attacker-
  influenced. This is the one place in the library where a bug is a denial of service rather than a
  leak.
- **`CREDIT_CARD` will match any 16-digit run** — order IDs, correlation IDs, timestamps
  concatenated together. Decide whether to add a Luhn check. It is about fifteen lines and it turns
  a noisy rule into a precise one.
- The prefilter must never produce a false negative. If you skip the regex when there is no `@`,
  you have just decided that no non-email pattern contains an `@` — check that against every
  pattern you ship, and re-check when you add one.
- Overlapping matches. Decide what happens when two patterns match the same span.

**APIs to look up.** `Pattern.compile`, `Matcher.find`, `Matcher.appendReplacement` /
`appendTail`, `String.indexOf`.

**Done when.**
```
should_mask_email_when_present_in_message
should_mask_every_occurrence_when_message_has_several
should_leave_message_unchanged_when_nothing_matches
should_not_match_order_id_when_luhn_check_is_enabled
```

---

## Step 5 — One entry point

**Build.** A single facade over Steps 1–4: give it an argument object, get back the masked
replacement; give it a formatted string, get back the pattern-masked string.

**Done when.** `./mvnw -pl log-guard-core test` is green and the module has zero Spring and zero
Logback imports. Verify that literally — grep the module for `org.springframework` and
`ch.qos.logback` and expect nothing.

---

# Phase 2 — Logback

## Step 6 — Spike first, code second

Before writing anything real, write a throwaway test that attaches a `ListAppender<ILoggingEvent>`
to a logger, logs an object, and asserts on what the captured event actually contains.

Find out for yourself:
- What is in `getArgumentArray()` versus `getFormattedMessage()`?
- When is `getFormattedMessage()` computed — eagerly, or on first call and then cached?
- What happens to `getArgumentArray()` after the message has been formatted once?

That third answer determines the whole of Step 7. Delete the spike afterwards; the point is the
knowledge, not the file.

---

## Step 7 — A delegating logging event

**Build.** Something that implements `ILoggingEvent`, wraps a real one, and returns masked values
for the channels you care about while delegating everything else unchanged.

**For v0.1, mask two channels only:** `getArgumentArray()` and `getFormattedMessage()`. MDC,
key-value pairs and throwables are v0.2 — the architecture lists all five, but shipping two working
channels beats five half-finished ones.

**The decision.** Either you replace the arguments and let something else format the message, or
you let Logback format and then mask the resulting string. Doing both naively means a value gets
masked twice, and `#a3f91c` hashed again is not `#a3f91c`.

Pick one as the primary path for objects and use the other for the pattern layer, and be explicit
about the order.

**Traps.**
- `ILoggingEvent` has a lot of methods. Every one you forget to delegate is a subtle bug in
  somebody's appender — timestamps, thread name, logger name, level, caller data, markers.
- `getFormattedMessage()` on the wrapped event may already be cached from before you wrapped it.
- `prepareForDeferredProcessing()` exists for a reason. Find out what it does before you ignore it.

**APIs to look up.** `ch.qos.logback.classic.spi.ILoggingEvent`, `MessageFormatter` from
`org.slf4j.helpers`.

**Done when.**
```
should_mask_annotated_argument_when_event_is_wrapped
should_delegate_logger_name_and_level_unchanged_when_wrapping
should_not_double_mask_when_both_layers_are_enabled
```

---

## Step 8 — An appender wrapper

**Build.** An `Appender<ILoggingEvent>` that holds a delegate, wraps each incoming event, and
passes it on.

**Traps.**
- Lifecycle. Logback silently drops events from an appender that was never `start()`ed. Propagate
  `start()` and `stop()` to the delegate, and get the ordering right.
- `getName()` / `setName()` — Logback uses names to resolve appender references. A wrapper with no
  name will confuse status output at best.
- Do not swallow exceptions from the delegate, and do not let a masking failure lose a log event.
  Decide now: if masking throws, do you drop the event or pass it through unmasked? Both are
  defensible; one of them is a leak. Write the reasoning in a comment — this is a genuine WHY.

**APIs to look up.** `ch.qos.logback.core.Appender`, `UnsynchronizedAppenderBase`,
`ch.qos.logback.core.spi.LifeCycle`.

**Done when.**
```
should_pass_masked_event_to_delegate_when_appending
should_start_delegate_when_wrapper_starts
should_pass_event_through_unmasked_when_masking_fails
```

---

## Step 9 — Attach the wrapper to the context

**Build.** Walk the `LoggerContext`, and for each logger, replace each attached appender with a
wrapped version.

**Traps.**
- **Idempotency.** Spring reconfigures logging more than once during startup. Wrapping a wrapper
  means double-masking. Make the operation safe to run twice.
- **`AsyncAppender`.** Wrap the appenders it *references*, not the async appender itself —
  otherwise masking runs on the caller's thread and you have put regex work on the request path.
  This is the whole point of the ordering in the architecture doc.
- Concurrent modification. You are iterating a logger's appenders while detaching and re-attaching
  them.

**APIs to look up.** `LoggerContext.getLoggerList()`, `Logger.iteratorForAppenders()`,
`detachAppender`, `addAppender`, `AsyncAppender`.

**Done when.**
```
should_wrap_every_attached_appender_when_applied
should_not_wrap_twice_when_applied_repeatedly
should_wrap_referenced_appenders_when_async_appender_is_present
```

---

# Phase 3 — Spring

## Step 10 — Make the auto-configuration do the work

The properties and `LogGuardAutoConfiguration` already exist and bind correctly. Now give it
behaviour: build the engine from the properties and apply Step 9.

**Traps.**
- **Fail fast on the salt.** If any registered strategy is `HASH` and `hashSalt` is blank, throw
  `MissingHashSaltException`. It already exists.
- **Timing.** Boot configures Logback very early, long before your beans exist. Anything logged
  before the wrapper is attached is unmasked. Find out how early you can hook in, and then write
  down honestly in the README what the gap is. Do not pretend it is zero.
- Respect `log-guard.enabled: false` — including not paying the startup cost.

**Look into.** `ApplicationListener<ApplicationEnvironmentPreparedEvent>` versus a plain
`@Bean` + `InitializingBean` versus `LoggerContextListener`. They hook at different times and the
tradeoff is real.

---

## Step 11 — The demo test

**Build.** The one that makes the whole library obvious.

Real Postgres via the existing `BaseIntegrationTest`, an entity with an `@Pii` email, and
`org.hibernate.orm.jdbc.bind` at `TRACE`. Query by email. Capture the log output with a
`ListAppender` and assert the address does not appear in it.

This test is the demo, the README example and the video. It is worth more than the other tests
combined, because the leak comes from a YAML setting rather than from any line of application
code — and it is the one no static scanner reports.

**Done when.**
```
should_not_log_email_when_hibernate_bind_tracing_is_enabled
```

---

# v0.1 is done when

- [ ] `./mvnw test` green from a clean clone
- [ ] `log-guard-core` has no Spring and no Logback imports
- [ ] Adding the starter to a Boot app masks `log.info("{}", entity)` with no other configuration
- [ ] The Hibernate bind-parameter test passes
- [ ] README's before/after example is copy-pasted from real output, not written by hand
- [ ] Startup fails with a useful message when `HASH` is used without a salt

---

## Where to ask for help

Getting stuck for two days on Logback's appender lifecycle is not learning, it is attrition. Ask
for an explanation of how something works, then write the code yourself. That keeps the
understanding on your side of the keyboard.

Worth asking for:
- How a Logback/Spring internal actually behaves, and why
- A review of code you have already written — `/code-review`, or the `spring-boot-reviewer` agent
- Plumbing you did not set out to learn: poms, CI, Central publishing at v0.3
