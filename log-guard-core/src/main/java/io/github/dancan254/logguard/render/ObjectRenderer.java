package io.github.dancan254.logguard.render;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.NestingConfig;
import io.github.dancan254.logguard.mask.ValueMasker;
import io.github.dancan254.logguard.meta.PiiField;
import io.github.dancan254.logguard.meta.PiiMetadata;
import io.github.dancan254.logguard.meta.PiiMetadataCache;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ObjectRenderer {

    private static final String UNREADABLE = "<unreadable>";
    private static final String CYCLE = "<cycle>";
    private static final String TOO_DEEP = "<...>";

    private final ValueMasker valueMasker;
    private final NestingConfig nesting;

    public ObjectRenderer(ValueMasker valueMasker, NestingConfig nesting) {
        this.valueMasker = valueMasker;
        this.nesting = nesting == null ? NestingConfig.DEFAULT : nesting;
    }

    /**
     * A container is rendered even when its own class carries no metadata: the PII is in the
     * elements, and {@code List<Customer>} is as common a log argument as a customer.
     */
    public boolean shouldRender(Object target) {
        return isContainer(target) || PiiMetadataCache.forClass(target.getClass()).hasPii();
    }

    public String render(Object target) {
        return render(target, 0, new IdentityHashMap<>());
    }

    private String render(Object target, int depth, IdentityHashMap<Object, Boolean> onPath) {
        if (target == null) {
            return "null";
        }
        // A scalar still gets the metadata question asked of it: a value type of your own may
        // extend Number or CharSequence and carry @Pii.
        if (isScalar(target) && !PiiMetadataCache.forClass(target.getClass()).hasPii()) {
            return String.valueOf(target);
        }
        if (depth > nesting.maxDepth()) {
            return TOO_DEEP;
        }
        if (onPath.put(target, Boolean.TRUE) != null) {
            return CYCLE;
        }
        try {
            return renderContainerOrObject(target, depth, onPath);
        } finally {
            onPath.remove(target);
        }
    }

    private String renderContainerOrObject(Object target, int depth,
                                           IdentityHashMap<Object, Boolean> onPath) {
        if (target instanceof Collection<?> collection) {
            return renderElements(collection, collection.size(), depth, onPath);
        }
        if (target instanceof Object[] array) {
            return renderElements(java.util.Arrays.asList(array), array.length, depth, onPath);
        }
        if (target instanceof Map<?, ?> map) {
            return renderMap(map, depth, onPath);
        }
        PiiMetadata metadata = PiiMetadataCache.forClass(target.getClass());
        if (!metadata.hasPii() || !nesting.allowsReflectionInto(target.getClass())) {
            return String.valueOf(target);
        }
        return renderFields(target, metadata, depth, onPath);
    }

    private String renderFields(Object target, PiiMetadata metadata, int depth,
                                IdentityHashMap<Object, Boolean> onPath) {
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
            rendered.append(field.name()).append('=').append(renderField(field, target, depth, onPath));
        }
        return rendered.append(')').toString();
    }

    private String renderField(PiiField field, Object target, int depth,
                               IdentityHashMap<Object, Boolean> onPath) {
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
            return render(value, depth + 1, onPath);
        }
        return valueMasker.mask(String.valueOf(value), field.strategy());
    }

    private String renderElements(Iterable<?> elements, int size, int depth,
                                  IdentityHashMap<Object, Boolean> onPath) {
        StringBuilder rendered = new StringBuilder("[");
        int shown = 0;
        for (Object element : elements) {
            if (shown == nesting.maxElements()) {
                break;
            }
            if (shown > 0) {
                rendered.append(", ");
            }
            rendered.append(render(element, depth + 1, onPath));
            shown++;
        }
        appendOverflow(rendered, size - shown);
        return rendered.append(']').toString();
    }

    private String renderMap(Map<?, ?> map, int depth, IdentityHashMap<Object, Boolean> onPath) {
        StringBuilder rendered = new StringBuilder("{");
        int shown = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (shown == nesting.maxElements()) {
                break;
            }
            if (shown > 0) {
                rendered.append(", ");
            }
            rendered.append(render(entry.getKey(), depth + 1, onPath))
                    .append('=')
                    .append(render(entry.getValue(), depth + 1, onPath));
            shown++;
        }
        appendOverflow(rendered, map.size() - shown);
        return rendered.append('}').toString();
    }

    private static void appendOverflow(StringBuilder rendered, int remaining) {
        if (remaining <= 0) {
            return;
        }
        if (rendered.length() > 1) {
            rendered.append(", ");
        }
        rendered.append("…(+").append(remaining).append(" more)");
    }

    private static boolean isContainer(Object target) {
        return target instanceof Collection<?> || target instanceof Map<?, ?>
                || target instanceof Object[];
    }

    /** Primitive arrays land here too: they hold no reference to reflect into. */
    private static boolean isScalar(Object target) {
        return target instanceof CharSequence || target instanceof Number
                || target instanceof Boolean || target instanceof Character
                || target instanceof Enum<?>;
    }
}
