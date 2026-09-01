# log-guard

Keeps personal data out of your logs.

```java
@Entity
@Data
public class Customer {
    @Id private Long id;
    @Pii(strategy = HASH)    private String email;
    @Pii(strategy = PARTIAL) private String phoneNumber;
    @Pii                     private LocalDate dateOfBirth;
}
```

```java
log.info("Processing customer {}", customer);
```

Without log-guard, Lombok's generated `toString()` puts this in your log aggregator. With it on the
classpath — and no other configuration — the console shows this instead, pasted from a run of the
demo module:

```
i.g.d.l.demo.customer.CustomerService - Registered customer Customer(id=3, email=#47f5f4, phoneNumber=+2547****244, nationalId=***, dateOfBirth=***, city=Kisumu)
```

Masking happens on the logging **event**, not the pattern layout, so the same masked text is what
reaches an OpenTelemetry collector over OTLP:

```
otel-collector-1  | Body: Str(Registered customer Customer(id=3, email=#47f5f4, phoneNumber=+2547****244, nationalId=***, dateOfBirth=***, city=Kisumu))
```

The pattern layer catches what no annotation can — output from code you do not own. Hibernate's
bind-parameter trace, from the same run:

```
org.hibernate.orm.jdbc.bind - binding parameter (1:VARCHAR) <- [Kisumu]
org.hibernate.orm.jdbc.bind - binding parameter (2:DATE) <- [1991-07-02]
org.hibernate.orm.jdbc.bind - binding parameter (3:VARCHAR) <- [***]
```

And the channel that leaks without anyone writing a log statement — the driver's own exception:

```
Caused by: java.sql.SQLException: duplicate key value violates unique constraint: Key (email)=(***) already exists
```

## Install

```xml
<dependency>
    <groupId>io.github.dancan254</groupId>
    <artifactId>log-guard-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Annotate a field, log the object. There is nothing else to switch on — `smoke/verify.sh` in this
repository builds a Boot app with that dependency and nothing else, and asserts both layers mask.

Optional, if you want them: `log-guard-log4j2` (a `RewritePolicy` for applications on Log4j2) and
`log-guard-jackson` (a Jackson module for JSON-encoded logs, registered on a mapper you build for
the purpose — never on your application's primary one).

## What it will not do

- It cannot mask what is logged before it installs: Boot's banner and its own first startup lines.
- A bare name in a plain string is undetectable. Neither layer can see it.
- It is not a compliance guarantee, and not a static scanner. It changes what reaches the appender
  at runtime; it never reads your code.

## Configuration

Every property, with its default:

```yaml
log-guard:
  enabled: true
  hash-salt: ""                 # required if any field uses HASH
  on-failure: PLACEHOLDER       # or DROP, PASSTHROUGH
  type-aware:
    enabled: true
  patterns:
    enabled: true
    built-in: [EMAIL, IBAN, CREDIT_CARD, PHONE_E164]
    max-message-length: 8192
    custom: []                  # name, regex, strategy
  mdc:
    redact-keys: []             # emptied whatever they hold
  nesting:
    max-depth: 3
    max-elements: 10
    base-packages: []           # empty means "any class carrying @Pii"
  validation:
    unannotated-entity: WARN    # or FAIL for CI, OFF to skip
```

## Performance

JMH, `./mvnw -Pbench -pl log-guard-benchmarks verify`. Numbers below are from a loaded developer
laptop (JDK 25, 12 cores, other containers running), so read them as an order of magnitude and as
a comparison between rows, not as a spec sheet.

| Benchmark | ns/op |
|---|---|
| Argument with no `@Pii` anywhere (the common case) | **8** |
| A log line with nothing to mask, through the pattern layer | **126** |
| Rendering an annotated 6-field entity | 1,519 |
| Rendering a 20-field entity | 1,614 |
| Rendering a list of three annotated entities | 4,745 |
| A line that does contain an email, masked | 7,137 |
| Both layers together on one event | 25,452 |

The first row is the one that matters: an argument whose class carries no `@Pii` costs a
`ClassValue` lookup and nothing else. The pattern layer only reaches the regex when a message
actually satisfies some enabled pattern's minimum requirements — before that check existed, any
line containing a single digit paid 14 µs.

CI runs the same benchmarks on every push with fewer forks and iterations, then checks each score
against a ceiling in `log-guard-benchmarks/check-thresholds.sh`. The ceilings sit about ten times
above the numbers above, because a shared runner cannot produce a publishable measurement and a
benchmark that fails on noise gets deleted within a month. They are set to catch the shape of
regression this project has already had once, where a clean line went from 126 ns to 14,000.

## Modules

| Module | Purpose |
|---|---|
| `log-guard-core` | `@Pii`, strategies, masking engine. Zero dependencies. |
| `log-guard-logback` | Logback appender wrapping. |
| `log-guard-spring-boot-starter` | Auto-configuration, properties, startup validator. |
| `log-guard-benchmarks` | JMH harness. Never published, `-Pbench` only. |

## Licence

Apache-2.0
