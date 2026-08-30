package io.github.dancan254.logguard.render;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.mask.ValueMasker;
import io.github.dancan254.logguard.meta.PiiField;
import io.github.dancan254.logguard.meta.PiiMetadata;
import io.github.dancan254.logguard.meta.PiiMetadataCache;

public final class ObjectRenderer {

    private static final String UNREADABLE = "<unreadable>";

    private final ValueMasker valueMasker;

    public ObjectRenderer(ValueMasker valueMasker) {
        this.valueMasker = valueMasker;
    }

    public String render(Object target) {
        if (target == null) {
            return "null";
        }
        PiiMetadata metadata = PiiMetadataCache.forClass(target.getClass());
        if (!metadata.hasPii()) {
            return String.valueOf(target);
        }

        StringBuilder rendered = new StringBuilder(target.getClass().getSimpleName()).append('(');
        boolean first = true;
        for (PiiField field : metadata.fields()) {
            if (field.strategy() == MaskStrategy.DROP) {
                continue;
            }
            if (!first) {
                rendered.append(", ");
            }
            first = false;
            rendered.append(field.name()).append('=').append(renderField(field, target));
        }
        return rendered.append(')').toString();
    }

    private String renderField(PiiField field, Object target) {
        Object value;
        try {
            value = field.accessor().get(target);
        } catch (ReflectiveOperationException | RuntimeException cause) {
            // A lazy association outside a transaction, or a field we were refused access to.
            // Never propagate: a privacy library that throws from inside a log statement is not
            // deployable.
            return UNREADABLE;
        }
        if (value == null) {
            return "null";
        }
        if (!field.isMasked()) {
            return String.valueOf(value);
        }
        return valueMasker.mask(String.valueOf(value), field.strategy());
    }
}
