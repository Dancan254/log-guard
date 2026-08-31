package io.github.dancan254.logguard;

import io.github.dancan254.logguard.mask.ValueMasker;
import io.github.dancan254.logguard.render.ObjectRenderer;
import io.github.dancan254.logguard.pattern.PatternMasker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LogGuardMasker {

    private final ObjectRenderer objectRenderer;
    private final PatternMasker patternMasker;
    private final ValueMasker valueMasker;
    private final Set<String> mdcRedactKeys;
    private final FailureMode onFailure;

    public LogGuardMasker(MaskingConfig config) {
        this.valueMasker = new ValueMasker(config.hashSalt());
        this.objectRenderer = config.typeAwareEnabled()
                ? new ObjectRenderer(valueMasker, config.nesting())
                : null;
        this.patternMasker = config.patternsEnabled()
                ? new PatternMasker(config.builtInPatterns(), config.customPatterns(), valueMasker)
                : null;
        this.mdcRedactKeys = config.mdcRedactKeys();
        this.onFailure = config.onFailure();
    }

    public FailureMode onFailure() {
        return onFailure;
    }

    /**
     * Returns the argument itself when there is nothing to render, so the overwhelmingly common
     * case allocates nothing and downstream formatting is untouched.
     */
    public Object maskArgument(Object argument) {
        if (objectRenderer == null || argument == null || argument instanceof CharSequence) {
            return argument;
        }
        if (!objectRenderer.shouldRender(argument)) {
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

    /**
     * A key on the redact list is emptied whatever it holds — an MDC value survives every log call
     * on the thread, so a name or an account number in there outlives the request that set it.
     */
    public Map<String, String> maskMdc(Map<String, String> mdc) {
        if (mdc == null || mdc.isEmpty()) {
            return mdc;
        }
        Map<String, String> replaced = null;
        for (Map.Entry<String, String> entry : mdc.entrySet()) {
            String masked = maskMdcValue(entry.getKey(), entry.getValue());
            if (!Objects.equals(masked, entry.getValue()) && replaced == null) {
                replaced = new LinkedHashMap<>(mdc);
            }
            if (replaced != null) {
                replaced.put(entry.getKey(), masked);
            }
        }
        return replaced == null ? mdc : Collections.unmodifiableMap(replaced);
    }

    private String maskMdcValue(String key, String value) {
        if (value == null) {
            return null;
        }
        if (key != null && mdcRedactKeys.contains(key.toLowerCase(Locale.ROOT))) {
            return valueMasker.mask(value, MaskStrategy.REDACT);
        }
        return maskMessage(value);
    }
}
