package io.github.dancan254.logguard.pattern;

import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.MaskingConfig;
import io.github.dancan254.logguard.mask.ValueMasker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Input nobody would write by hand but anyone can send: a field padded until the regex is the
 * slowest thing in the request.
 */
class AdversarialInputTest {

    private static final Duration BUDGET = Duration.ofSeconds(2);

    private static PatternMasker maskerFor(BuiltInPattern pattern) {
        return new PatternMasker(List.of(pattern), List.of(), new ValueMasker("pepper"),
                MaskingConfig.DEFAULT_MAX_MESSAGE_LENGTH);
    }

    private static String repeat(String unit, int times) {
        return unit.repeat(times);
    }

    @ParameterizedTest
    @EnumSource(BuiltInPattern.class)
    void should_finish_within_budget_on_a_long_run_of_digits(BuiltInPattern pattern) {
        String message = repeat("9", 40_000);

        assertTimeoutPreemptively(BUDGET, () -> maskerFor(pattern).mask(message));
    }

    @ParameterizedTest
    @EnumSource(BuiltInPattern.class)
    void should_finish_within_budget_on_a_near_miss_repeated(BuiltInPattern pattern) {
        String message = repeat("jane.wanjiru@acme", 3_000);

        assertTimeoutPreemptively(BUDGET, () -> maskerFor(pattern).mask(message));
    }

    @ParameterizedTest
    @EnumSource(BuiltInPattern.class)
    void should_finish_within_budget_on_alternating_separators(BuiltInPattern pattern) {
        String message = repeat("1-2 3-4 ", 5_000);

        assertTimeoutPreemptively(BUDGET, () -> maskerFor(pattern).mask(message));
    }

    @Test
    void should_truncate_a_message_past_the_configured_limit() {
        PatternMasker masker = new PatternMasker(List.of(BuiltInPattern.EMAIL), List.of(),
                new ValueMasker("pepper"), 64);

        String masked = masker.mask(repeat("x", 100));

        assertThat(masked).endsWith(PatternMasker.TRUNCATION_NOTICE);
    }

    @Test
    void should_still_mask_the_head_of_a_truncated_message() {
        PatternMasker masker = new PatternMasker(List.of(BuiltInPattern.EMAIL), List.of(),
                new ValueMasker("pepper"), 64);

        String masked = masker.mask("jane.wanjiru@acme.io " + repeat("x", 200));

        assertThat(masked).startsWith("***").doesNotContain("jane.wanjiru");
    }

    @Test
    void should_drop_the_tail_it_never_examined() {
        PatternMasker masker = new PatternMasker(List.of(BuiltInPattern.EMAIL), List.of(),
                new ValueMasker("pepper"), 32);

        String masked = masker.mask(repeat("x", 40) + "jane.wanjiru@acme.io");

        assertThat(masked).doesNotContain("acme.io");
    }

    @Test
    void should_leave_a_message_at_the_limit_untruncated() {
        PatternMasker masker = new PatternMasker(List.of(BuiltInPattern.EMAIL), List.of(),
                new ValueMasker("pepper"), 32);

        String masked = masker.mask(repeat("x", 32));

        assertThat(masked).isEqualTo(repeat("x", 32));
    }

    @Test
    void should_not_emit_the_head_of_an_address_split_by_the_cap() {
        PatternMasker masker = new PatternMasker(List.of(BuiltInPattern.EMAIL), List.of(),
                new ValueMasker("pepper"), 32);

        // The cap lands inside the address, so the fragment before it matches no pattern and the
        // old cut printed it raw. Any part of the local part surviving is the leak.
        String masked = masker.mask(repeat("x", 24) + " jane.wanjiru@acme.io");

        assertThat(masked).doesNotContain("jane");
    }
}
