# Contributing to log-guard

Thanks for taking an interest. This is a privacy library, so the bar for a change is a little
higher than usual: a bug here does not crash an application, it quietly prints a customer's email
address into a log aggregator.

## Before you write code

Open an issue first for anything that is not a small fix. A masking library gains most of its value
from what it refuses to do, so a change that adds a dependency, a new abstraction or a new public
API needs agreement on the design before the code exists.

Small and welcome without discussion:

- A new built-in pattern, with tests covering both what it matches and what it must not.
- A false positive or false negative in an existing pattern.
- Documentation, including anything in this file that turned out to be wrong.

Worth an issue first:

- A new module, a new configuration property or a change to `@Pii`.
- Anything touching the event wrapper or the async boundary.
- Anything that would add a dependency to `log-guard-core`.

## Requirements

- **Java 25.** The reactor sets `maven.compiler.release` to 25 and the CI runners use Temurin 25.
- **Docker**, for the Testcontainers integration tests and the demo module.
- No local Maven install needed; use the wrapper (`./mvnw`).

## Build and test

```bash
./mvnw test        # unit and Testcontainers integration tests
./mvnw package     # module jars
```

Run the demo when you change masking behaviour, because it is the only place the OTLP path is
visible:

```bash
./mvnw -pl log-guard-demo spring-boot:run                       # listens on 8081
docker compose -f log-guard-demo/compose.yaml logs -f otel-collector
```

The benchmarks live outside the normal build and need a quiet machine:

```bash
./mvnw -Pbench -pl log-guard-benchmarks verify
```

## The rules that are not negotiable

**`log-guard-core` has zero dependencies, and stays that way.** That constraint is the product, not
an accident. A privacy library with a transitive dependency tree is a harder sell to the team that
has to approve it. Spring, Logback and Jackson all live in adapter modules.

**Mask the event, never the layout.** The OpenTelemetry Logback appender reads
`event.getFormattedMessage()` directly, so a layout-level filter redacts the console and still
exports raw personal data over OTLP. Anything that moves masking into a layout or an encoder
defeats the point of the library. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#central-decision-mask-the-event-not-the-layout).

**Fail closed.** When masking cannot be completed, the event must not be emitted unmasked. The
`on-failure` property decides between a placeholder and dropping the event; `PASSTHROUGH` is a
deliberate choice by the user, never a fallback the library takes on its own. The same reasoning
governs `max-message-length`: past the cap the tail is truncated rather than skipped, because
skipping the regex on long input is a leak anyone can trigger by padding a field.

**Container image tags are pinned.** `postgres:18-alpine`, never `:latest`. Never pin
`testcontainers.version` by hand — it comes from the Spring Boot BOM.

## Tests

Every behavioural change needs a test. A masking change needs a test that fails on the old code.

- Unit tests for pure logic, named `*Test`.
- Integration tests for anything needing a container or a Spring context, named `*IntegrationTest`.
- Test methods read as documentation: `should_<expected>_when_<condition>`.
- One assertion concept per test.

Adversarial cases belong in `AdversarialInputTest`. If your pattern can be defeated by padding,
unicode or a token boundary, that is the test to add it to.

## Code style

- Comments explain **why**, never what. A comment restating the code is removed in review.
- No `// TODO` in committed code. Open an issue instead.
- Early returns over nested conditionals. No `else` after a `return` or `throw`.
- No abbreviations in names.
- Keep changes to the task at hand. A drive-by refactor inside a bug fix makes the fix
  unreviewable.

## Commits and pull requests

Conventional commits, imperative mood, lowercase, no trailing period:

```
type(scope): short description

Optional body — the WHY, not the what.
```

Types: `feat` · `fix` · `refactor` · `test` · `chore` · `ci` · `docs`.

The scope is the module or feature slice, e.g. `fix(patterns): stop matching a bare port number`.

- Subject line max 72 characters, and it must say what changed. `wip`, `updates`, `fix stuff` and
  `address review comments` are not acceptable messages.
- Add a body only when the why is not obvious from the subject. Explain the reasoning, not the
  diff — the diff is already in the commit.
- Keep commits atomic. A commit that fixes a bug and reformats a file cannot be reverted cleanly.
- Run `./mvnw test` before you commit. Do not commit build output; `target/` and generated poms are
  not part of a change.

### Branching and pushing

**Never push to `main`.** Every change arrives through a pull request, so that CI has run before
anything lands.

The maintainer commits documentation and release chores — a changelog entry, a version bump, a
corrected link — straight to `main`. Every change to library code goes through a pull request, no
matter who wrote it.

```bash
git checkout main && git pull
git checkout -b fix/short-kebab-description
# work, commit
git push -u origin fix/short-kebab-description
gh pr create --base main
```

External contributors work from a fork and open the pull request from their branch there; the flow
is otherwise identical.

- Branch from an up-to-date `main`, named `type/short-kebab-description`.
- One concern per branch. A second idea is a second branch.
- Never force-push `main`, and never rewrite history that someone else may already have pulled.
  Force-pushing your own pull request branch is fine while it is under review.
- Keep the branch current by rebasing on `main` or merging `main` into it, whichever you prefer.

### Pull requests

The title follows the same rules as a commit subject. The body says what changed, why, and how to
test it. Link the issue it closes.

Pull requests are squash-merged, so the pull request title becomes the commit message on `main` —
write it accordingly. A pull request needs a green CI run before it can be merged. CI runs four
jobs:

| Job | What it proves |
| :--- | :--- |
| `test` | The unit and integration suites pass. |
| `build` | The module jars build. |
| `clean-app` | A Boot app whose only configuration is the dependency still auto-configures. |
| `benchmarks` | No score crossed its ceiling in `log-guard-benchmarks/check-thresholds.sh`. |

The `clean-app` job runs `smoke/verify.sh` and catches what no unit test can: the starter failing
to install itself in a real application.

If the benchmark job fails, look at the ceilings before you raise them. They sit roughly ten times
above the measured numbers precisely so that noise on a shared runner does not fail a build, which
means a failure is usually real.

## Changelog

User-visible changes get an entry under `## [Unreleased]` in `CHANGELOG.md`. Internal refactors and
test-only changes do not.

## Releasing

Maintainers only, and documented separately in [`docs/RELEASING.md`](docs/RELEASING.md). Publishing
is deliberately not push-button: a tag stages a deployment and a human clicks Publish, because a
released version can never be withdrawn.

## Licence

Contributions are made under the Apache 2.0 licence, as in [LICENSE](LICENSE).
