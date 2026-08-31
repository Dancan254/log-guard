package io.github.dancan254.logguard;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MdcMaskingTest {

    private final LogGuardMasker masker = new LogGuardMasker(new MaskingConfig(true, true,
            List.of(BuiltInPattern.EMAIL), List.of(), "pepper",
            Set.of("customerName"), NestingConfig.DEFAULT, FailureMode.PLACEHOLDER, MaskingConfig.DEFAULT_MAX_MESSAGE_LENGTH));

    @Test
    void should_redact_listed_key_whatever_its_value_holds() {
        assertThat(masker.maskMdc(Map.of("customerName", "Jane Wanjiru")))
                .containsEntry("customerName", "***");
    }

    @Test
    void should_match_listed_key_without_regard_to_case() {
        assertThat(masker.maskMdc(Map.of("CUSTOMERNAME", "Jane Wanjiru")))
                .containsEntry("CUSTOMERNAME", "***");
    }

    @Test
    void should_apply_the_pattern_layer_to_an_unlisted_key() {
        assertThat(masker.maskMdc(Map.of("actor", "jane.wanjiru@acme.io")))
                .containsEntry("actor", "***");
    }

    @Test
    void should_leave_a_value_holding_nothing_sensitive_untouched() {
        assertThat(masker.maskMdc(Map.of("requestId", "7f3a-11")))
                .containsEntry("requestId", "7f3a-11");
    }

    @Test
    void should_return_the_same_map_when_nothing_changed() {
        Map<String, String> mdc = Map.of("requestId", "7f3a-11");

        assertThat(masker.maskMdc(mdc)).isSameAs(mdc);
    }

    @Test
    void should_keep_entries_that_precede_the_first_masked_one() {
        Map<String, String> mdc = new LinkedHashMap<>();
        mdc.put("requestId", "7f3a-11");
        mdc.put("actor", "jane.wanjiru@acme.io");

        assertThat(masker.maskMdc(mdc)).containsEntry("requestId", "7f3a-11");
    }
}
