<!-- The title follows the commit convention: type(scope): short description -->

## What changed

<!-- One or two sentences. The diff shows the how; this says the what. -->

## Why

<!-- The reasoning, or the issue this closes. -->

Closes #

## How to test it

<!-- The commands a reviewer runs, and what they should see. -->

```bash
./mvnw test
```

## Checklist

- [ ] `./mvnw test` passes locally.
- [ ] A behavioural change has a test that fails on the old code.
- [ ] No new dependency in `log-guard-core`, or the pull request explains why it belongs there.
- [ ] Masking still happens on the event, not in a layout or an encoder.
- [ ] No unmasked output on a failure path.
- [ ] `CHANGELOG.md` updated under `## [Unreleased]`, if the change is user-visible.
- [ ] Comments explain why rather than what, and no `// TODO` is left behind.
