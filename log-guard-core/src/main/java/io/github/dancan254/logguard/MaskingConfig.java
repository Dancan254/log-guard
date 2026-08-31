package io.github.dancan254.logguard;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record MaskingConfig(
        boolean typeAwareEnabled,
        boolean patternsEnabled,
        List<BuiltInPattern> builtInPatterns,
        List<CustomPattern> customPatterns,
        String hashSalt,
        Set<String> mdcRedactKeys,
        NestingConfig nesting,
        FailureMode onFailure,
        int maxMessageLength) {

    /** Long enough for any real log line; short enough that a padded field cannot cost much. */
    public static final int DEFAULT_MAX_MESSAGE_LENGTH = 8192;

    public MaskingConfig {
        builtInPatterns = builtInPatterns == null ? List.of() : List.copyOf(builtInPatterns);
        customPatterns = customPatterns == null ? List.of() : List.copyOf(customPatterns);
        mdcRedactKeys = mdcRedactKeys == null ? Set.of() : mdcRedactKeys.stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        nesting = nesting == null ? NestingConfig.DEFAULT : nesting;
        onFailure = onFailure == null ? FailureMode.PLACEHOLDER : onFailure;
        maxMessageLength = maxMessageLength <= 0 ? DEFAULT_MAX_MESSAGE_LENGTH : maxMessageLength;
    }

    public record CustomPattern(String name, String regex, MaskStrategy strategy) {
    }
}
