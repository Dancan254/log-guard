package io.github.dancan254.logguard.render;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.Pii;
import io.github.dancan254.logguard.mask.ValueMasker;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectRendererTest {

    private static class Customer {
        Long id = 42L;
        @Pii(strategy = MaskStrategy.HASH)
        String email = "jane.wanjiru@acme.io";
        @Pii(strategy = MaskStrategy.PARTIAL)
        String phoneNumber = "+254712345891";
        @Pii
        LocalDate dateOfBirth = LocalDate.of(1994, 3, 11);
        @Pii(strategy = MaskStrategy.DROP)
        String passwordHash = "argon2id$v=19$secret";
        String note;
    }

    private static class Anonymous {
        @Override
        public String toString() {
            return "hand-written";
        }
    }

    /** AtomicInteger's private field lives in a java.base package that is not open to us. */
    private static class Unreadable extends AtomicInteger {
        @Pii
        String email = "jane.wanjiru@acme.io";
    }

    private final ObjectRenderer renderer = new ObjectRenderer(new ValueMasker("pepper"));

    @Test
    void should_mask_annotated_field_when_rendering() {
        assertThat(renderer.render(new Customer())).contains("phoneNumber=+2547****891");
    }

    @Test
    void should_leave_unannotated_field_visible_when_rendering() {
        assertThat(renderer.render(new Customer())).contains("id=42");
    }

    @Test
    void should_render_null_field_as_null() {
        assertThat(renderer.render(new Customer())).contains("note=null");
    }

    @Test
    void should_return_original_to_string_when_class_has_no_annotations() {
        assertThat(renderer.render(new Anonymous())).isEqualTo("hand-written");
    }

    @Test
    void should_omit_field_entirely_when_strategy_is_drop() {
        assertThat(renderer.render(new Customer())).doesNotContain("passwordHash");
    }

    @Test
    void should_render_placeholder_when_field_read_throws() {
        assertThat(renderer.render(new Unreadable())).contains("<unreadable>");
    }

    @Test
    void should_render_null_target_as_null() {
        assertThat(renderer.render(null)).isEqualTo("null");
    }
}
