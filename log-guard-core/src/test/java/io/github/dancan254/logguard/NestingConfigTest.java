package io.github.dancan254.logguard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NestingConfigTest {

    @Test
    void should_allow_exact_package_match() {
        NestingConfig config = new NestingConfig(3, 10, List.of("com.example"));

        assertThat(config.allowsReflectionInto(com.example.Dummy.class)).isTrue();
    }

    @Test
    void should_allow_child_package_match() {
        NestingConfig config = new NestingConfig(3, 10, List.of("com.example"));

        assertThat(config.allowsReflectionInto(com.example.child.Dummy.class)).isTrue();
    }

    @Test
    void should_reject_sibling_package_with_shared_prefix() {
        NestingConfig config = new NestingConfig(3, 10, List.of("com.example"));

        assertThat(config.allowsReflectionInto(com.examplefoo.Dummy.class)).isFalse();
    }

    @Test
    void should_allow_any_class_when_base_packages_is_empty() {
        NestingConfig config = NestingConfig.DEFAULT;

        assertThat(config.allowsReflectionInto(com.examplefoo.Dummy.class)).isTrue();
    }
}
