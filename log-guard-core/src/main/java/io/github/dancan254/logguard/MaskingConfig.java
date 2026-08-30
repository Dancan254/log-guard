package io.github.dancan254.logguard;

import java.util.List;

public record MaskingConfig(
        boolean typeAwareEnabled,
        boolean patternsEnabled,
        List<BuiltInPattern> builtInPatterns,
        List<CustomPattern> customPatterns,
        String hashSalt) {

    public MaskingConfig {
        builtInPatterns = builtInPatterns == null ? List.of() : List.copyOf(builtInPatterns);
        customPatterns = customPatterns == null ? List.of() : List.copyOf(customPatterns);
    }

    public record CustomPattern(String name, String regex, MaskStrategy strategy) {
    }
}
