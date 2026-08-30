package io.github.dancan254.logguard;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Prints the before/after that the README quotes, so the README is never written by hand. */
class MaskingShowcaseTest {

    private static class Customer {
        Long id = 42L;
        @Pii(strategy = MaskStrategy.HASH)
        String email = "jane.wanjiru@acme.io";
        @Pii(strategy = MaskStrategy.PARTIAL)
        String phoneNumber = "+254712345891";
        @Pii
        LocalDate dateOfBirth = LocalDate.of(1994, 3, 11);

        @Override
        public String toString() {
            return "Customer(id=" + id + ", email=" + email
                    + ", phoneNumber=" + phoneNumber + ", dateOfBirth=" + dateOfBirth + ")";
        }
    }

    @Test
    void should_mask_every_annotated_field_when_rendering_the_readme_example() {
        LogGuardMasker masker = new LogGuardMasker(new MaskingConfig(true, true,
                List.of(BuiltInPattern.EMAIL, BuiltInPattern.PHONE_E164), List.of(), "log-guard-demo-salt"));

        Customer customer = new Customer();
        String before = "Processing customer " + customer;
        String after = "Processing customer " + masker.maskArgument(customer);

        System.out.println();
        System.out.println("  before  " + before);
        System.out.println("  after   " + masker.maskMessage(after));
        System.out.println();

        assertThat(after).doesNotContain("jane.wanjiru@acme.io", "+254712345891", "1994-03-11");
    }
}
