# Releasing

How a version of log-guard gets from `main` to Maven Central.

Publishing is deliberately not push-button. The workflow uploads a deployment and stops; a human
clicks Publish. A released version can never be overwritten or withdrawn, so the manual gate is
the point.

## What triggers what

| Event | Runs | Result |
|---|---|---|
| Push / pull request | `ci.yml` | test, build, benchmarks, clean-app |
| Push a `v*` tag | `release.yml` | tests, then a **staged** deployment |
| Click Publish in the portal | — | artifacts go public, permanently |

Everything is driven by the tag. Merging a version bump publishes nothing.

## One-time setup

Needed once per machine and per repository, not per release.

### 1. Claim the namespace

The groupId is `io.github.dancan254`, so ownership is proved through the GitHub account of the same
name — no DNS record involved.

Sign in at https://central.sonatype.com, go to **Namespaces**, add `io.github.dancan254`, and it
returns a verification code. Create a public repository named exactly that code, click **Verify
Namespace**, then delete it:

```bash
gh repo create <code> --public --description "Sonatype namespace verification"
# verify in the portal, then
gh repo delete <code> --yes
```

### 2. Publish the GPG public key

Central validates every signature against a public keyserver and rejects a deployment it cannot
check.

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <FINGERPRINT>

curl -s -o /dev/null -w "%{http_code}\n" \
  "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x<FINGERPRINT>"
```

A `200` means it propagated. Keyservers never delete a key, so this cannot be undone.

### 3. Set the repository secrets

The username and password are a **user token** from **View Account → Generate User Token** in the
portal, not the portal login.

```bash
gh secret set MAVEN_CENTRAL_USERNAME
gh secret set MAVEN_CENTRAL_PASSWORD
gh secret set MAVEN_GPG_PASSPHRASE

gpg --armor --export-secret-keys <FINGERPRINT> | gh secret set GPG_PRIVATE_KEY
```

Piping the key keeps the private material out of the shell history and off disk. Confirm with
`gh secret list` — `release.yml` reads exactly these four names and fails on a missing one only
after the test job has already passed.

## Releasing a version

### 1. Bump the version

Every module inherits the reactor version, so one command covers all of them:

```bash
./mvnw versions:set -DnewVersion=<X.Y.Z> -DgenerateBackupPoms=false
```

Update `project.build.outputTimestamp` in the reactor pom to the release date. It is fixed per
release so the same source tree always builds byte-identical jars.

Update `CHANGELOG.md`, then open a pull request and let CI go green before merging.

### 2. Preflight the release profile

This runs what the publish job runs — sources, javadoc, GPG signing — and stops before uploading.
It is how a broken signature is found without spending a tag.

```bash
./mvnw -Prelease -DskipTests verify
ls log-guard-core/target/*.asc
```

A passphrase prompt here is the value that belongs in `MAVEN_GPG_PASSPHRASE`.

### 3. Tag

```bash
git checkout main && git pull
git tag -s v<X.Y.Z> -m "log-guard <X.Y.Z>"
git push origin v<X.Y.Z>
```

The tag is signed with the SSH signing key, which is unrelated to the GPG key that signs the
artifacts — GitHub verifies the tag, Central verifies the jars.

### 4. Watch the workflow

```bash
gh run watch
```

`test` runs first and gates `publish`, so nothing is uploaded from a tag that has not passed the
same tests as a pull request.

### 5. Publish the deployment

Open https://central.sonatype.com/publishing/deployments. The deployment sits in `VALIDATED`.
Check the component list, then click **Publish**.

Artifacts reach `repo1.maven.org` within about ten minutes and the search index within a few hours:

```bash
curl -s "https://repo1.maven.org/maven2/io/github/dancan254/log-guard-core/<X.Y.Z>/" | head
```

### 6. Cut the GitHub release

The tag alone does not create one:

```bash
gh release create v<X.Y.Z> --title "v<X.Y.Z>" --generate-notes
```

## What gets published

The four library modules, plus the reactor pom. `log-guard-demo` and `log-guard-benchmarks` set
`maven.deploy.skip`, because a demo application and a JMH harness are not things anyone should be
able to add as a dependency.

## When it goes wrong

**A deployment stuck in `FAILED`** — open it in the portal; the validation report names the
missing piece. Usually a missing javadoc or sources jar, or a signature whose key is not on a
keyserver. Drop it, fix, and re-tag with a new patch version.

**`gpg: signing failed: Inappropriate ioctl for device`** — the CI agent has no tty. The
`--pinentry-mode loopback` argument in the release profile is what prevents this; it is not
optional.

**`401` from Central** — the token was regenerated. Tokens do not expire on their own, but
generating a new one silently invalidates the old.

**A version published by mistake** — it cannot be removed. Publish the next patch version and mark
the bad one deprecated in `CHANGELOG.md`.
