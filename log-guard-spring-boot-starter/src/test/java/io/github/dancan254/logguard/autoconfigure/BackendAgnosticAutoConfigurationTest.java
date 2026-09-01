package io.github.dancan254.logguard.autoconfigure;

import io.github.dancan254.logguard.LogGuardMasker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An application on Log4j2 has no Logback on its classpath. Gating the auto-configuration on
 * Logback left such an application with masking (the listener still installs it) but no startup
 * validator and no masker bean — working enough to look fine, and silently missing half the
 * library.
 */
class BackendAgnosticAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogGuardAutoConfiguration.class));

    @Test
    void should_configure_the_masker_when_logback_is_absent() {
        runner.withClassLoader(new FilteredClassLoader("ch.qos.logback"))
                .run(context -> assertThat(context).hasSingleBean(LogGuardMasker.class));
    }

    @Test
    void should_configure_the_entity_validator_when_logback_is_absent() {
        runner.withClassLoader(new FilteredClassLoader("ch.qos.logback"))
                .run(context -> assertThat(context).hasSingleBean(EntityAnnotationValidator.class));
    }

    @Test
    void should_skip_the_entity_validator_when_jpa_is_absent() {
        runner.withClassLoader(new FilteredClassLoader("jakarta.persistence"))
                .run(context -> assertThat(context).doesNotHaveBean(EntityAnnotationValidator.class));
    }

    @Test
    void should_configure_nothing_when_disabled() {
        runner.withPropertyValues("log-guard.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LogGuardMasker.class));
    }
}
