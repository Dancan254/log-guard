package io.github.dancan254.logguard.autoconfigure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class SpringConfigurationMetadataTest {

    @Test
    void should_generate_spring_configuration_metadata_for_log_guard_properties() throws IOException {
        var resources = Collections.list(
                getClass().getClassLoader().getResources("META-INF/spring-configuration-metadata.json"));
        assertThat(resources).as("at least one spring-configuration-metadata.json should be present").isNotEmpty();

        boolean found = false;
        for (var resource : resources) {
            try (var input = resource.openStream()) {
                var metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                if (metadata.contains("log-guard.enabled") && metadata.contains("log-guard.hash-salt")) {
                    found = true;
                    break;
                }
            }
        }

        assertThat(found).as("the starter's metadata should describe log-guard.* properties").isTrue();
    }
}
