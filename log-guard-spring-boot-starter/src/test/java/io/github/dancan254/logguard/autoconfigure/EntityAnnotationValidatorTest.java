package io.github.dancan254.logguard.autoconfigure;

import io.github.dancan254.logguard.exception.MissingHashSaltException;
import io.github.dancan254.logguard.exception.UnannotatedEntityException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityAnnotationValidatorTest {

    private static final List<String> ENTITIES =
            List.of("io.github.dancan254.logguardfixtures.entities");

    private static EntityAnnotationValidator validator(ValidationMode mode, boolean hasHashSalt) {
        return new EntityAnnotationValidator(mode, hasHashSalt, ENTITIES,
                EntityAnnotationValidatorTest.class.getClassLoader());
    }

    @Test
    void should_fail_startup_when_mode_is_fail_and_an_entity_is_unannotated() {
        assertThatThrownBy(() -> validator(ValidationMode.FAIL, true).afterPropertiesSet())
                .isInstanceOf(UnannotatedEntityException.class)
                .hasMessageContaining("LegacyCustomer");
    }

    @Test
    void should_name_every_unannotated_field_when_it_fails() {
        assertThatThrownBy(() -> validator(ValidationMode.FAIL, true).afterPropertiesSet())
                .hasMessageContaining("email")
                .hasMessageContaining("phoneNumber");
    }

    @Test
    void should_leave_a_field_outside_the_taxonomy_out_of_the_report() {
        assertThatThrownBy(() -> validator(ValidationMode.FAIL, true).afterPropertiesSet())
                .extracting(Throwable::getMessage)
                .asString()
                .doesNotContain("city");
    }

    @Test
    void should_ignore_an_entity_that_declares_no_to_string() {
        assertThatThrownBy(() -> validator(ValidationMode.FAIL, true).afterPropertiesSet())
                .extracting(Throwable::getMessage)
                .asString()
                .doesNotContain("QuietCustomer");
    }

    @Test
    void should_ignore_an_entity_whose_fields_are_annotated() {
        assertThatThrownBy(() -> validator(ValidationMode.FAIL, true).afterPropertiesSet())
                .extracting(Throwable::getMessage)
                .asString()
                .doesNotContain("AnnotatedCustomer");
    }

    @Test
    void should_only_warn_when_mode_is_warn() {
        assertThatCode(() -> validator(ValidationMode.WARN, true).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void should_skip_the_scan_entirely_when_mode_is_off() {
        assertThatCode(() -> validator(ValidationMode.OFF, false).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void should_reject_a_hash_strategy_when_no_salt_is_configured() {
        assertThatThrownBy(() -> validator(ValidationMode.WARN, false).afterPropertiesSet())
                .isInstanceOf(MissingHashSaltException.class);
    }

    @Test
    void should_treat_an_underscored_column_name_as_the_same_field_name() {
        assertThat(PiiFieldNames.isSensitive("date_of_birth")).isTrue();
    }

    @Test
    void should_not_flag_a_name_outside_the_taxonomy() {
        assertThat(PiiFieldNames.isSensitive("city")).isFalse();
    }
}
