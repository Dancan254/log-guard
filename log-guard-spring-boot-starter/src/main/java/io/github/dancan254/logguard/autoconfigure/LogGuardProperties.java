package io.github.dancan254.logguard.autoconfigure;

import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.FailureMode;
import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.NestingConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("log-guard")
public record LogGuardProperties(

        @DefaultValue("true") boolean enabled,

        String hashSalt,

        @DefaultValue TypeAware typeAware,

        @DefaultValue Patterns patterns,

        @DefaultValue Mdc mdc,

        @DefaultValue Nesting nesting,

        @DefaultValue("PLACEHOLDER") FailureMode onFailure,

        @DefaultValue Validation validation) {

    public record TypeAware(@DefaultValue("true") boolean enabled) {
    }

    /** Keys whose value is redacted whatever it holds, matched without regard to case. */
    public record Mdc(List<String> redactKeys) {

        public Mdc {
            redactKeys = redactKeys == null ? List.of() : List.copyOf(redactKeys);
        }
    }

    public record Nesting(

            @DefaultValue("3") int maxDepth,

            @DefaultValue("10") int maxElements,

            List<String> basePackages) {

        public Nesting {
            basePackages = basePackages == null ? List.of() : List.copyOf(basePackages);
        }

        public NestingConfig toNestingConfig() {
            return new NestingConfig(maxDepth, maxElements, basePackages);
        }
    }

    public record Patterns(

            @DefaultValue("true") boolean enabled,

            @DefaultValue({"EMAIL", "IBAN", "CREDIT_CARD", "PHONE_E164"}) List<BuiltInPattern> builtIn,

            List<CustomPattern> custom,

            @DefaultValue("8192") int maxMessageLength) {

        public Patterns {
            custom = custom == null ? List.of() : List.copyOf(custom);
        }
    }

    public record CustomPattern(String name, String regex, @DefaultValue("REDACT") MaskStrategy strategy) {
    }

    public record Validation(@DefaultValue("WARN") ValidationMode unannotatedEntity)  {
    }
}
