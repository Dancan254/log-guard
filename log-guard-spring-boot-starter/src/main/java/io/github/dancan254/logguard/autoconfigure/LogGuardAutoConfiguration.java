package io.github.dancan254.logguard.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import io.github.dancan254.logguard.LogGuardMasker;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

// Deliberately not conditional on any logging backend: the masker and the validator are
// backend-agnostic, and gating them on Logback left a Log4j2 application with no validator at all.
@AutoConfiguration
@ConditionalOnProperty(prefix = "log-guard", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(LogGuardProperties.class)
public class LogGuardAutoConfiguration {

    /**
     * The listener has normally installed masking already. This runs it again for contexts built
     * without the full Boot startup, and is safe because installation is idempotent.
     */
    @Bean
    @ConditionalOnMissingBean
    LogGuardMasker logGuardMasker(LogGuardProperties properties) {
        return LogGuardInstallation.apply(properties);
    }

    @Bean
    @ConditionalOnClass(name = EntityAnnotationValidator.ENTITY_ANNOTATION)
    @ConditionalOnMissingBean
    EntityAnnotationValidator logGuardEntityAnnotationValidator(LogGuardProperties properties,
                                                                BeanFactory beanFactory) {
        boolean hasHashSalt = properties.hashSalt() != null && !properties.hashSalt().isBlank();
        return new EntityAnnotationValidator(properties.validation().unannotatedEntity(), hasHashSalt,
                basePackages(beanFactory), getClass().getClassLoader());
    }

    /** The app's own packages, so the scan never walks a dependency's entities. */
    private static List<String> basePackages(BeanFactory beanFactory) {
        return AutoConfigurationPackages.has(beanFactory)
                ? AutoConfigurationPackages.get(beanFactory)
                : List.of();
    }
}
