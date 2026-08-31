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
        FailureMode onFailure) {

    public MaskingConfig {
        builtInPatterns = builtInPatterns == null ? List.of() : List.copyOf(builtInPatterns);
        customPatterns = customPatterns == null ? List.of() : List.copyOf(customPatterns);
        mdcRedactKeys = mdcRedactKeys == null ? Set.of() : mdcRedactKeys.stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        nesting = nesting == null ? NestingConfig.DEFAULT : nesting;
        onFailure = onFailure == null ? FailureMode.PLACEHOLDER : onFailure;
    }

    public record CustomPattern(String name, String regex, MaskStrategy strategy) {
    }
}
