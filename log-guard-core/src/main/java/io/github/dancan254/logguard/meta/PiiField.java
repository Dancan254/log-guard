package io.github.dancan254.logguard.meta;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.PiiCategory;

import java.lang.reflect.Field;

/** {@code strategy} and {@code category} are null for a field carrying no {@code @Pii}. */
public record PiiField(String name, Field accessor, MaskStrategy strategy, PiiCategory category) {

    public boolean isMasked() {
        return strategy != null;
    }
}
