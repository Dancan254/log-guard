package io.github.dancan254.logguard;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LogGuardMaskerTest {

    private static class Customer {
        Long id = 42L;
        @Pii(strategy = MaskStrategy.PARTIAL)
        String email = "jane.wanjiru@acme.io";
    }

    private static class Order {
        Long id = 7L;
        String reference = "ORD-7";

        @Override
        public String toString() {
            return "Order(id=" + id + ", reference=" + reference + ")";
        }
    }

    private static MaskingConfig config(boolean typeAware, boolean patterns) {
        return new MaskingConfig(typeAware, patterns,
                List.of(BuiltInPattern.EMAIL), List.of(), "pepper",
                Set.of(), NestingConfig.DEFAULT, FailureMode.PLACEHOLDER);
    }

    @Test
    void should_apply_type_layer_then_pattern_layer_when_both_enabled() {
        LogGuardMasker masker = new LogGuardMasker(config(true, true));

        String rendered = String.valueOf(masker.maskArgument(new Customer()));

        assertThat(masker.maskMessage(rendered)).isEqualTo("Customer(id=42, email=j****@acme.io)");
    }

    @Test
    void should_skip_type_layer_when_disabled() {
        LogGuardMasker masker = new LogGuardMasker(config(false, true));

        Customer customer = new Customer();

        assertThat(masker.maskArgument(customer)).isSameAs(customer);
    }

    @Test
    void should_skip_pattern_layer_when_disabled() {
        LogGuardMasker masker = new LogGuardMasker(config(true, false));

        assertThat(masker.maskMessage("mailed jane.wanjiru@acme.io")).isEqualTo("mailed jane.wanjiru@acme.io");
    }

    @Test
    void should_return_argument_untouched_when_class_has_no_annotations() {
        LogGuardMasker masker = new LogGuardMasker(config(true, true));

        Order order = new Order();

        assertThat(masker.maskArgument(order)).isSameAs(order);
    }
}
