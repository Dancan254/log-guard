package io.github.dancan254.logguard.autoconfigure;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.Pii;
import io.github.dancan254.logguard.exception.MissingHashSaltException;
import io.github.dancan254.logguard.exception.UnannotatedEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the entity nobody remembered to annotate. A Lombok {@code toString} prints every field it
 * was not told to skip, and the first time that object reaches a log statement the field names in
 * the taxonomy are the ones that matter.
 */
class EntityAnnotationValidator implements InitializingBean {

    static final String ENTITY_ANNOTATION = "jakarta.persistence.Entity";

    private static final Logger log = LoggerFactory.getLogger(EntityAnnotationValidator.class);

    private final ValidationMode mode;
    private final boolean hasHashSalt;
    private final List<String> basePackages;
    private final ClassLoader classLoader;

    EntityAnnotationValidator(ValidationMode mode, boolean hasHashSalt, List<String> basePackages,
                              ClassLoader classLoader) {
        this.mode = mode;
        this.hasHashSalt = hasHashSalt;
        this.basePackages = basePackages;
        this.classLoader = classLoader;
    }

    @Override
    public void afterPropertiesSet() {
        if (mode == ValidationMode.OFF || basePackages.isEmpty()) {
            return;
        }
        Map<Class<?>, List<String>> findings = new LinkedHashMap<>();
        boolean hashInUse = false;

        for (Class<?> entity : scanForEntities()) {
            hashInUse |= usesHashStrategy(entity);
            List<String> unannotated = unannotatedSensitiveFields(entity);
            if (!unannotated.isEmpty()) {
                findings.put(entity, unannotated);
            }
        }

        if (hashInUse && !hasHashSalt) {
            throw new MissingHashSaltException();
        }
        report(findings);
    }

    private void report(Map<Class<?>, List<String>> findings) {
        if (findings.isEmpty()) {
            return;
        }
        String report = describe(findings);
        if (mode == ValidationMode.FAIL) {
            throw new UnannotatedEntityException(report);
        }
        log.warn("{}", report);
    }

    private static String describe(Map<Class<?>, List<String>> findings) {
        StringBuilder report = new StringBuilder("""
                log-guard found entity fields that look like personal data and carry no @Pii. \
                Anything that logs one of these objects logs the value in full:""");
        findings.forEach((entity, fields) -> report.append(System.lineSeparator())
                .append("  ").append(entity.getName()).append(" — ").append(String.join(", ", fields)));
        return report.append(System.lineSeparator())
                .append("Annotate each field with @Pii, or @Pii(strategy = DROP) to leave it out of the ")
                .append("rendered output entirely. Lombok's @ToString.Exclude is source-retained and ")
                .append("cannot be seen at runtime, so it does not silence this. ")
                .append("Set log-guard.validation.unannotated-entity: off to stop checking.")
                .toString();
    }

    private List<Class<?>> scanForEntities() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(entityFilter());
        List<Class<?>> entities = new ArrayList<>();
        for (String basePackage : basePackages) {
            for (BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
                resolve(definition.getBeanClassName()).ifPresent(entities::add);
            }
        }
        return entities;
    }

    /** Matched by name so the starter needs no compile-time dependency on the JPA API. */
    private static TypeFilter entityFilter() {
        return (metadataReader, factory) ->
                metadataReader.getAnnotationMetadata().hasAnnotation(ENTITY_ANNOTATION);
    }

    private java.util.Optional<Class<?>> resolve(String className) {
        try {
            return java.util.Optional.of(ClassUtils.forName(className, classLoader));
        } catch (ClassNotFoundException | LinkageError cause) {
            // An entity we cannot load is an entity we cannot advise on; it is not a startup failure.
            log.debug("log-guard could not load {} while validating entities", className, cause);
            return java.util.Optional.empty();
        }
    }

    private static boolean usesHashStrategy(Class<?> entity) {
        for (Field field : entity.getDeclaredFields()) {
            Pii annotation = field.getAnnotation(Pii.class);
            if (annotation != null && annotation.strategy() == MaskStrategy.HASH) {
                return true;
            }
        }
        return false;
    }

    /**
     * Only classes that declare a {@code toString} are reported. Without one the object prints as
     * a class name and a hash, and no field of it has ever reached a log.
     */
    private static List<String> unannotatedSensitiveFields(Class<?> entity) {
        if (!declaresToString(entity)) {
            return List.of();
        }
        List<String> unannotated = new ArrayList<>();
        for (Field field : entity.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.isAnnotationPresent(Pii.class) || !PiiFieldNames.isSensitive(field.getName())) {
                continue;
            }
            unannotated.add(field.getName());
        }
        return unannotated;
    }

    private static boolean declaresToString(Class<?> entity) {
        try {
            entity.getDeclaredMethod("toString");
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }
}
