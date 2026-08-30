package io.github.dancan254.logguard.mask;

import io.github.dancan254.logguard.MaskStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValueMaskerTest {

    private final ValueMasker masker = new ValueMasker("pepper");

    @Test
    void should_redact_fully_when_strategy_is_redact() {
        assertThat(masker.mask("jane.wanjiru@acme.io", MaskStrategy.REDACT)).isEqualTo("***");
    }

    @Test
    void should_keep_domain_when_partial_masking_an_email() {
        assertThat(masker.mask("jane.wanjiru@acme.io", MaskStrategy.PARTIAL)).isEqualTo("j****@acme.io");
    }

    @Test
    void should_keep_head_and_tail_when_partial_masking_a_long_value() {
        assertThat(masker.mask("+254712345891", MaskStrategy.PARTIAL)).isEqualTo("+2547****891");
    }

    @Test
    void should_keep_only_the_tail_when_partial_masking_a_medium_value() {
        assertThat(masker.mask("31234567", MaskStrategy.PARTIAL)).isEqualTo("****567");
    }

    @Test
    void should_degrade_to_redact_when_value_is_too_short_for_partial() {
        assertThat(masker.mask("ab", MaskStrategy.PARTIAL)).isEqualTo("***");
    }

    @Test
    void should_use_fixed_width_mask_when_partial_so_length_does_not_leak() {
        String shorter = masker.mask("+254712345891", MaskStrategy.PARTIAL);
        String longer = masker.mask("+2547123458910000", MaskStrategy.PARTIAL);

        assertThat(shorter).hasSameSizeAs(longer);
    }

    @Test
    void should_produce_stable_digest_when_same_value_and_salt() {
        String first = masker.mask("jane.wanjiru@acme.io", MaskStrategy.HASH);
        String second = new ValueMasker("pepper").mask("jane.wanjiru@acme.io", MaskStrategy.HASH);

        assertThat(first).isEqualTo(second).matches("#[0-9a-f]{6}");
    }

    @Test
    void should_produce_different_digest_when_salt_differs() {
        String mine = masker.mask("jane.wanjiru@acme.io", MaskStrategy.HASH);
        String theirs = new ValueMasker("other").mask("jane.wanjiru@acme.io", MaskStrategy.HASH);

        assertThat(mine).isNotEqualTo(theirs);
    }

    @Test
    void should_degrade_to_redact_when_hash_is_used_without_salt() {
        assertThat(new ValueMasker("  ").mask("jane.wanjiru@acme.io", MaskStrategy.HASH)).isEqualTo("***");
    }
}
