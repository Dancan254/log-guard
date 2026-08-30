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

Scaffold only — the masking engine is not implemented yet. See `CLAUDE.md` for the architecture
and build order.

## Modules

| Module | Purpose |
|---|---|
| `log-guard-core` | `@Pii`, strategies, masking engine. Zero dependencies. |
| `log-guard-logback` | Logback appender wrapping. |
| `log-guard-spring-boot-starter` | Auto-configuration and properties. |

## Licence

Apache-2.0
