package io.github.dancan254.logguard;

import java.util.List;

/**
 * {@code basePackages} empty means "reflect into any class carrying {@code @Pii}". Naming your own
 * packages narrows that further; nothing outside them is ever reflected into.
 */
public record NestingConfig(int maxDepth, int maxElements, List<String> basePackages) {

    public static final int DEFAULT_MAX_DEPTH = 3;
    public static final int DEFAULT_MAX_ELEMENTS = 10;

    public static final NestingConfig DEFAULT =
            new NestingConfig(DEFAULT_MAX_DEPTH, DEFAULT_MAX_ELEMENTS, List.of());

    public NestingConfig {
        basePackages = basePackages == null ? List.of() : List.copyOf(basePackages);
    }

    public boolean allowsReflectionInto(Class<?> type) {
        if (basePackages.isEmpty()) {
            return true;
        }
        String name = type.getName();
        for (String basePackage : basePackages) {
            if (name.startsWith(basePackage)) {
                return true;
            }
        }
        return false;
    }
}
