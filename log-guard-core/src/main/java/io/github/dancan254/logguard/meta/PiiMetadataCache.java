package io.github.dancan254.logguard.meta;

import io.github.dancan254.logguard.Pii;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PiiMetadataCache {

    /**
     * ClassValue rather than a map keyed on Class: it does not pin the class or its loader, which
     * a library-held map would, and that is a redeploy leak in any container.
     */
    private static final ClassValue<PiiMetadata> CACHE = new ClassValue<>() {
        @Override
        protected PiiMetadata computeValue(Class<?> type) {
            return scan(type);
        }
    };

    private PiiMetadataCache() {
    }

    public static PiiMetadata forClass(Class<?> type) {
        if (type == null) {
            return PiiMetadata.NONE;
        }
        return CACHE.get(type);
    }

    private static PiiMetadata scan(Class<?> type) {
        Map<String, Pii> recordAnnotations = recordAnnotationsByComponent(type);
        List<PiiField> fields = new ArrayList<>();
        boolean hasPii = false;

        for (Class<?> current : hierarchyFromRoot(type)) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                makeReadable(field);
                Pii annotation = field.getAnnotation(Pii.class);
                if (annotation == null) {
                    annotation = recordAnnotations.get(field.getName());
                }
                hasPii |= annotation != null;
                fields.add(new PiiField(field.getName(), field,
                        annotation == null ? null : annotation.strategy(),
                        annotation == null ? null : annotation.category()));
            }
        }
        return new PiiMetadata(fields, hasPii);
    }

    /** Superclass first, so inherited fields read in the order a person declared them. */
    private static List<Class<?>> hierarchyFromRoot(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.addFirst(current);
        }
        return hierarchy;
    }

    /** {@code @Pii} targets RECORD_COMPONENT, so on a record it need not reach the backing field. */
    private static Map<String, Pii> recordAnnotationsByComponent(Class<?> type) {
        if (!type.isRecord()) {
            return Map.of();
        }
        Map<String, Pii> annotations = new HashMap<>();
        for (RecordComponent component : type.getRecordComponents()) {
            Pii annotation = component.getAnnotation(Pii.class);
            if (annotation != null) {
                annotations.put(component.getName(), annotation);
            }
        }
        return annotations;
    }

    private static void makeReadable(Field field) {
        try {
            field.setAccessible(true);
        } catch (RuntimeException ignored) {
            // The field stays in the metadata; reading it will fail and render as unreadable.
        }
    }
}
