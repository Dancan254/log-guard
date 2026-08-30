package io.github.dancan254.logguard.pattern;

import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.MaskingConfig;
import io.github.dancan254.logguard.mask.ValueMasker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class PatternMaskerTest {

    private final PatternMasker masker = new PatternMasker(
            List.of(BuiltInPattern.values()), List.of(), new ValueMasker("pepper"));

    @Test
    void should_mask_email_when_present_in_message() {
        assertThat(masker.mask("binding parameter [1] as [VARCHAR] - [jane.wanjiru@acme.io]"))
                .isEqualTo("binding parameter [1] as [VARCHAR] - [***]");
    }

    @Test
    void should_mask_every_occurrence_when_message_has_several() {
        assertThat(masker.mask("merging jane@acme.io into john@acme.io"))
                .isEqualTo("merging *** into ***");
    }

    @Test
    void should_leave_message_unchanged_when_nothing_matches() {
        String message = "reconciled order 42 in 300 ms";

        assertThat(masker.mask(message)).isSameAs(message);
    }

    @Test
    void should_skip_regex_when_prefilter_finds_no_trigger_character() {
        String message = "no address and no digits here";

        assertThat(masker.mask(message)).isSameAs(message);
    }

    @Test
    void should_mask_card_number_when_luhn_check_passes() {
        assertThat(masker.mask("charged 4539578763621486")).isEqualTo("charged ***");
    }

    @Test
    void should_not_mask_order_id_when_luhn_check_fails() {
        assertThat(masker.mask("order 1234567890123456")).isEqualTo("order 1234567890123456");
    }

    @Test
    void should_mask_phone_number_when_present_in_message() {
        assertThat(masker.mask("calling +254712345891 now")).isEqualTo("calling *** now");
    }

    @Test
    void should_not_remask_output_produced_by_the_type_layer() {
        String typeMasked = "Customer(email=j****@acme.io, reference=#a3f91c)";

        assertThat(masker.mask(typeMasked)).isEqualTo(typeMasked);
    }

    @Test
    void should_apply_custom_pattern_with_its_own_strategy() {
        PatternMasker withCustom = new PatternMasker(List.of(),
                List.of(new MaskingConfig.CustomPattern("account", "ACC-\\d{10}", MaskStrategy.PARTIAL)),
                new ValueMasker("pepper"));

        assertThat(withCustom.mask("debited ACC-0123456789")).isEqualTo("debited ACC-0****789");
    }

    @Test
    void should_complete_within_a_second_when_input_is_adversarial() {
        String adversarial = "a".repeat(5_000) + "@" + "1".repeat(5_000);

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> masker.mask(adversarial));
    }
}
