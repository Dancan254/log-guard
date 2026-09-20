package io.github.dancan254.logguard.render;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.Pii;
import io.github.dancan254.logguard.NestingConfig;
import io.github.dancan254.logguard.mask.ValueMasker;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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
    private static class JdkSubclass extends AtomicInteger {
        @Pii(strategy = MaskStrategy.PARTIAL)
        String email = "jane.wanjiru@acme.io";
    }

    private final ObjectRenderer renderer = new ObjectRenderer(new ValueMasker("pepper"), NestingConfig.DEFAULT);

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
    void should_mask_user_fields_without_unreadable_jdk_superclass_fields() {
        assertThat(renderer.render(new JdkSubclass()))
                .isEqualTo("JdkSubclass(email=j****@acme.io)");
    }

    @Test
    void should_render_collection_by_to_string_when_elements_have_no_pii() {
        List<String> labels = List.of("one", "two");

        assertThat(renderer.render(labels)).isEqualTo(labels.toString());
    }

    @Test
    void should_render_map_by_to_string_when_entries_have_no_pii() {
        Map<String, String> labels = Map.of("a", "one", "b", "two");

        assertThat(renderer.render(labels)).isEqualTo(labels.toString());
    }

    @Test
    void should_render_collection_elements_when_it_may_contain_pii() {
        assertThat(renderer.render(List.of(new Customer())))
                .contains("phoneNumber=+2547****891");
    }

    @Test
    void should_render_array_elements_even_when_scalars() {
        assertThat(renderer.render(new String[]{"one", "two"})).isEqualTo("[one, two]");
    }

    @Test
    void should_render_primitive_array_as_elements() {
        assertThat(renderer.render(new int[]{1, 2, 3})).isEqualTo("[1, 2, 3]");
    }

    @Test
    void should_render_optional_value_when_present() {
        assertThat(renderer.render(Optional.of(new Customer())))
                .contains("phoneNumber=+2547****891");
    }

    @Test
    void should_render_empty_optional_as_optional_empty() {
        assertThat(renderer.render(Optional.empty())).isEqualTo("Optional.empty");
    }

    @Test
    void should_render_stream_elements() {
        assertThat(renderer.render(Stream.of(new Customer())))
                .contains("phoneNumber=+2547****891");
    }

    @Test
    void should_cap_stream_elements_at_the_configured_limit() {
        assertThat(renderer.render(Stream.generate(() -> new Customer()).limit(12)))
                .endsWith(", …]");
    }

    @Test
    void should_render_null_target_as_null() {
        assertThat(renderer.render(null)).isEqualTo("null");
    }
}
