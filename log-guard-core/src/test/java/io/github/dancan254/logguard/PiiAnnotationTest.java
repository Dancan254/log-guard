package io.github.dancan254.logguard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiAnnotationTest {

    private static final class AnnotatedSample {
        @Pii
        private String email;

        @Pii(strategy = MaskStrategy.HASH, category = PiiCategory.CREDENTIAL)
        private String apiToken;
    }

    @Test
    void should_default_to_redact_when_strategy_unspecified() throws NoSuchFieldException {
        var annotation = AnnotatedSample.class.getDeclaredField("email").getAnnotation(Pii.class);

        assertThat(annotation.strategy()).isEqualTo(MaskStrategy.REDACT);
    }

    @Test
    void should_default_to_personal_when_category_unspecified() throws NoSuchFieldException {
        var annotation = AnnotatedSample.class.getDeclaredField("email").getAnnotation(Pii.class);

        assertThat(annotation.category()).isEqualTo(PiiCategory.PERSONAL);
    }

    @Test
    void should_retain_explicit_strategy_when_declared() throws NoSuchFieldException {
        var annotation = AnnotatedSample.class.getDeclaredField("apiToken").getAnnotation(Pii.class);

        assertThat(annotation.strategy()).isEqualTo(MaskStrategy.HASH);
    }
}
