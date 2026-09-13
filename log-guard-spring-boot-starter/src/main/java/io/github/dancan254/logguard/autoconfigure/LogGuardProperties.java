package io.github.dancan254.logguard.autoconfigure;

import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.FailureMode;
import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.NestingConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * The {@code log-guard.*} configuration surface.
 *
 * @param enabled   whether log-guard installs itself at all; masking is on by default
 * @param hashSalt  the salt for {@link MaskStrategy#HASH}. Required if any field or custom pattern
 *                  uses it, and treated as a secret — a blank salt fails startup rather than
 *                  quietly degrading
 * @param typeAware the annotation-driven layer, which reads {@code @Pii} metadata
 * @param patterns  the regex layer, which covers output from code you do not own
 * @param mdc       keys redacted wholesale from the MDC
 * @param nesting   how far rendering follows objects, collections and maps
 * @param onFailure what an appender receives when masking could not be completed; never unmasked
 *                  output unless explicitly chosen
 * @param validation the startup scan for entities that leak
 */
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

    /**
     * The annotation-driven layer: exact, and blind to classes you have not annotated.
     *
     * @param enabled whether {@code @Pii} metadata is read at all
     */
    public record TypeAware(@DefaultValue("true") boolean enabled) {
    }

    /**
     * Keys whose value is redacted whatever it holds, matched without regard to case.
     *
     * @param redactKeys the MDC keys to empty; never null once constructed
     */
    public record Mdc(List<String> redactKeys) {

        /**
         * Normalises an absent list to empty and copies it, so bound configuration cannot be
         * mutated afterwards.
         *
         * @param redactKeys the MDC keys to empty, or null when the property is unset
         */
        public Mdc {
            redactKeys = redactKeys == null ? List.of() : List.copyOf(redactKeys);
        }
    }

    /**
     * Bounds on following an annotated object into what it holds, so a log statement cannot become
     * a graph traversal.
     *
     * @param maxDepth     how many levels deep rendering follows
     * @param maxElements  how many entries of a collection, array or map are rendered
     * @param basePackages restricts reflection to these packages; empty means any class carrying
     *                     {@code @Pii}
     */
    public record Nesting(

            @DefaultValue("3") int maxDepth,

            @DefaultValue("10") int maxElements,

            List<String> basePackages) {

        /**
         * Normalises an absent package list to empty and copies it, so bound configuration cannot
         * be mutated afterwards.
         *
         * @param maxDepth     how many levels deep rendering follows
         * @param maxElements  how many entries of a collection, array or map are rendered
         * @param basePackages packages reflection may enter, or null when the property is unset
         */
        public Nesting {
            basePackages = basePackages == null ? List.of() : List.copyOf(basePackages);
        }

        /**
         * @return these limits as the engine's own configuration type, which knows nothing of Spring
         */
        public NestingConfig toNestingConfig() {
            return new NestingConfig(maxDepth, maxElements, basePackages);
        }
    }

    /**
     * The regex layer, which reaches output no annotation can.
     *
     * @param enabled          whether the formatted message is scanned at all
     * @param builtIn          the built-in patterns to apply; the list you set replaces the
     *                         defaults rather than adding to them
     * @param custom           your own named patterns
     * @param maxMessageLength how much of a message is scanned. Past the cap the head is masked and
     *                         the tail truncated at a token boundary: it fails closed, because
     *                         skipping the scan on long input is a leak anyone can trigger by
     *                         padding a field
     */
    public record Patterns(

            @DefaultValue("true") boolean enabled,

            @DefaultValue({"EMAIL", "IBAN", "CREDIT_CARD", "PHONE_E164"}) List<BuiltInPattern> builtIn,

            List<CustomPattern> custom,

            @DefaultValue("8192") int maxMessageLength) {

        /**
         * Normalises an absent custom-pattern list to empty and copies it, so bound configuration
         * cannot be mutated afterwards.
         *
         * @param enabled          whether the formatted message is scanned at all
         * @param builtIn          the built-in patterns to apply
         * @param custom           your own named patterns, or null when the property is unset
         * @param maxMessageLength how much of a message is scanned before the tail is truncated
         */
        public Patterns {
            custom = custom == null ? List.of() : List.copyOf(custom);
        }
    }

    /**
     * A pattern of your own, for identifiers only your systems issue.
     *
     * @param name     names the capture group, so it must be unique among configured patterns
     * @param regex    the expression to match; one that does not compile fails startup
     * @param strategy how a match is masked
     */
    public record CustomPattern(String name, String regex, @DefaultValue("REDACT") MaskStrategy strategy) {
    }

    /**
     * The startup scan for entities that would leak.
     *
     * @param unannotatedEntity what to do about an {@code @Entity} that declares a
     *                          {@code toString()} and holds a personal-data field carrying no
     *                          {@code @Pii}. {@code FAIL} in CI stops a new column reaching
     *                          production
     */
    public record Validation(@DefaultValue("WARN") ValidationMode unannotatedEntity)  {
    }
}
