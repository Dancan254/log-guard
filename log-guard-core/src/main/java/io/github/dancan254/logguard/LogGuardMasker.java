package io.github.dancan254.logguard;

import io.github.dancan254.logguard.mask.ValueMasker;
import io.github.dancan254.logguard.meta.PiiMetadataCache;
import io.github.dancan254.logguard.pattern.PatternMasker;
import io.github.dancan254.logguard.render.ObjectRenderer;

public final class LogGuardMasker {

    private final ObjectRenderer objectRenderer;
    private final PatternMasker patternMasker;

    public LogGuardMasker(MaskingConfig config) {
        ValueMasker valueMasker = new ValueMasker(config.hashSalt());
        this.objectRenderer = config.typeAwareEnabled() ? new ObjectRenderer(valueMasker) : null;
        this.patternMasker = config.patternsEnabled()
                ? new PatternMasker(config.builtInPatterns(), config.customPatterns(), valueMasker)
                : null;
    }

    /**
     * Returns the argument itself when its class carries no {@code @Pii}, so the overwhelmingly
     * common case allocates nothing and downstream formatting is untouched.
     */
    public Object maskArgument(Object argument) {
        if (objectRenderer == null || argument == null || argument instanceof CharSequence) {
            return argument;
        }
        if (!PiiMetadataCache.forClass(argument.getClass()).hasPii()) {
            return argument;
        }
        return objectRenderer.render(argument);
    }

    public String maskMessage(String message) {
        if (patternMasker == null) {
            return message;
        }
        return patternMasker.mask(message);
    }
}
