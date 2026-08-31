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

Without log-guard, Lombok's generated `toString()` puts this in your log aggregator:

```
Processing customer Customer(id=42, email=jane.wanjiru@acme.io, phoneNumber=+254712345891, dateOfBirth=1994-03-11)
```

With log-guard on the classpath:

```
Processing customer Customer(id=42, email=#a3f91c, phoneNumber=+2547****891, dateOfBirth=***)
```

Masking happens on the logging event, not the pattern layout, so your OTLP exporter sees the
redacted form too.

## Status

Working through v0.2: masking engine, Logback event wrapping, Spring auto-configuration, all five
event channels, nested objects, and the startup validator are in. See `CLAUDE.md` for the
architecture and `docs/IMPLEMENTATION-PLAN.md` for what is left.

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

## Modules

| Module | Purpose |
|---|---|
| `log-guard-core` | `@Pii`, strategies, masking engine. Zero dependencies. |
| `log-guard-logback` | Logback appender wrapping. |
| `log-guard-spring-boot-starter` | Auto-configuration, properties, startup validator. |
| `log-guard-benchmarks` | JMH harness. Never published, `-Pbench` only. |

## Licence

Apache-2.0
