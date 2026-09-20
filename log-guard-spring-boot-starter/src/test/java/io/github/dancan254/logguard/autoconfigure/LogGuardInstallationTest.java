package io.github.dancan254.logguard.autoconfigure;

import io.github.dancan254.logguard.MaskingConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogGuardInstallationTest {

    @Test
    void should_lowercase_mdc_redact_keys_when_converting_properties() {
        LogGuardProperties properties = new LogGuardProperties(
                true,
                "pepper",
                new LogGuardProperties.TypeAware(true),
                new LogGuardProperties.Patterns(true, List.of(), List.of(), 8192),
                new LogGuardProperties.Mdc(List.of("CUSTOMERNAME", "Actor")),
                new LogGuardProperties.Nesting(3, 10, List.of()),
                null,
                new LogGuardProperties.Validation(null));

        MaskingConfig config = LogGuardInstallation.toMaskingConfig(properties);

        assertThat(config.mdcRedactKeys())
                .containsExactlyInAnyOrder("customername", "actor");
    }
}
