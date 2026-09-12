# Security policy

## Supported versions

Only the latest released version receives fixes. There are no maintenance branches at this stage of
the project.

| Version | Supported |
| :--- | :--- |
| 0.1.x | Yes |
| < 0.1.0 | No |

## What counts as a vulnerability here

This is a masking library, so the interesting failures are leaks rather than crashes. Please report
privately, **not** as a public issue, anything in this list:

- **A masking bypass.** An input that reaches an appender unmasked when it should have been masked:
  a `@Pii` field rendered in the clear, a pattern defeated by padding, unicode, a token boundary or
  the `max-message-length` cut.
- **A channel that is not covered.** Personal data surviving in arguments, the formatted message,
  MDC, key-value pairs or the throwable chain despite configuration that should have masked it.
- **A failure that opens instead of closing.** Any path where masking fails and the event is
  emitted unmasked without `on-failure: PASSTHROUGH` having been set deliberately.
- **A leak through `HASH`.** Recovering an original value from a hash, or correlation surviving a
  salt rotation.
- **Catastrophic backtracking** in a built-in pattern, since a log statement is attacker-reachable
  in most applications.

A false positive — something masked that should not have been — is a normal bug. Open a public
issue for it.

## How to report

Use GitHub's private vulnerability reporting: open the
[Security tab](https://github.com/Dancan254/log-guard/security/advisories) of this repository and
choose **Report a vulnerability**. That creates a private advisory only the maintainers can read.

Please include:

- The version of log-guard, and your logging backend.
- The relevant `log-guard.*` configuration.
- A minimal reproduction: the annotated class or the pattern, the log statement, what the appender
  received, and what you expected instead.

Do not include real personal data in a report. Use obviously synthetic values.

## What to expect

- An acknowledgement within a few days.
- An assessment of whether it is a bypass and, if so, which versions are affected.
- A fix released as a new patch version, since a published version can never be withdrawn.
- Credit in the release notes and the advisory, unless you would rather stay anonymous.

Please give us a chance to ship a fix before disclosing publicly. This is a volunteer project, so a
reasonable window rather than a fixed deadline: we will tell you where the fix stands rather than
leave you waiting in silence.
