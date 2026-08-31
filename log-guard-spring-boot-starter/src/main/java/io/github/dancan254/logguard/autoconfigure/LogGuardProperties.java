package io.github.dancan254.logguard.autoconfigure;

import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.MaskStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("log-guard")
public record LogGuardProperties(

        @DefaultValue("true") boolean enabled,

        String hashSalt,

        @DefaultValue TypeAware typeAware,

        @DefaultValue Patterns patterns,

        @DefaultValue Validation validation) {

    public record TypeAware(@DefaultValue("true") boolean enabled) {
    }

    public record Patterns(

            @DefaultValue("true") boolean enabled,

            @DefaultValue({"EMAIL", "IBAN", "CREDIT_CARD", "PHONE_E164"}) List<BuiltInPattern> builtIn,

            List<CustomPattern> custom) {

        public Patterns {
            custom = custom == null ? List.of() : List.copyOf(custom);
        }
    }

    public record CustomPattern(String name, String regex, @DefaultValue("REDACT") MaskStrategy strategy) {
    }

    public record Validation(@DefaultValue("WARN") ValidationMode unannotatedEntity)  {
    }
}
