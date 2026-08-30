package io.github.dancan254.logguard.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class LogGuardAutoConfigurationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LogGuardProperties properties;

    @Test
    void should_bind_defaults_when_no_properties_configured() {
        assertThat(properties.enabled()).isTrue();
        assertThat(properties.typeAware().enabled()).isTrue();
    }

    @Test
    void should_default_validation_to_warn_when_unconfigured() {
        assertThat(properties.validation().unannotatedEntity()).isEqualTo(ValidationMode.WARN);
    }

    @Test
    void should_default_to_four_built_in_patterns_when_unconfigured() {
        assertThat(properties.patterns().builtIn()).hasSize(4);
    }
}
