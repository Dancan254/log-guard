package io.github.dancan254.logguard.pattern;

import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.MaskingConfig;
import io.github.dancan254.logguard.mask.ValueMasker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The prefilter is the only place in the library where a wrong answer is silent: it decides not to
 * look. Every case here is one where it must decide to look.
 */
class PrefilterTest {

    private static PatternMasker maskerFor(BuiltInPattern pattern) {
        return new PatternMasker(List.of(pattern), List.of(), new ValueMasker("pepper"),
                MaskingConfig.DEFAULT_MAX_MESSAGE_LENGTH);
    }

    @ParameterizedTest
    @EnumSource(BuiltInPattern.class)
    void should_still_mask_its_own_example_when_the_prefilter_runs(BuiltInPattern pattern) {
        String masked = maskerFor(pattern).mask(example(pattern));

        assertThat(masked).isEqualTo("***");
    }

    @ParameterizedTest
    @EnumSource(BuiltInPattern.class)
    void should_still_mask_its_own_example_inside_a_sentence(BuiltInPattern pattern) {
        String masked = maskerFor(pattern).mask("value was " + example(pattern) + " at 09:41");

        assertThat(masked).isEqualTo("value was *** at 09:41");
    }

    @Test
    void should_skip_a_line_whose_digits_cannot_reach_the_shortest_card() {
        PatternMasker masker = maskerFor(BuiltInPattern.CREDIT_CARD);

        assertThat(masker.mask("Order ORD-9 accepted in 41 ms"))
                .isEqualTo("Order ORD-9 accepted in 41 ms");
    }

    @Test
    void should_still_see_a_card_split_by_separators() {
        PatternMasker masker = maskerFor(BuiltInPattern.CREDIT_CARD);

        assertThat(masker.mask("card 4539 1488 0343 6467 charged")).isEqualTo("card *** charged");
    }

    @Test
    void should_leave_a_card_that_fails_the_luhn_check() {
        PatternMasker masker = maskerFor(BuiltInPattern.CREDIT_CARD);

        assertThat(masker.mask("ref 4539148803436460")).isEqualTo("ref 4539148803436460");
    }

    private static String example(BuiltInPattern pattern) {
        return switch (pattern) {
            case EMAIL -> "jane.wanjiru@acme.io";
            case IBAN -> "GB29NWBK60161331926819";
            case CREDIT_CARD -> "4539148803436467";
            case PHONE_E164 -> "+254712345891";
            case KENYAN_NATIONAL_ID -> "31234567";
        };
    }
}
