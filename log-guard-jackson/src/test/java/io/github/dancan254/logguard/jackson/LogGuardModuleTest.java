package io.github.dancan254.logguard.jackson;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.Pii;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class LogGuardModuleTest {

    public record Customer(
            Long id,
            @Pii(strategy = MaskStrategy.PARTIAL) String email,
            @Pii(strategy = MaskStrategy.HASH) String nationalId,
            @Pii(strategy = MaskStrategy.DROP) String passwordHash,
            String city) {
    }

    private final ObjectMapper masking = JsonMapper.builder()
            .addModule(new LogGuardModule("pepper"))
            .build();

    private static Customer customer() {
        return new Customer(42L, "jane.wanjiru@acme.io", "31234567", "argon2id$secret", "Nairobi");
    }

    @Test
    void should_mask_an_annotated_property_when_serializing() {
        assertThat(masking.writeValueAsString(customer())).contains("\"email\":\"j****@acme.io\"");
    }

    @Test
    void should_hash_a_property_asking_for_a_hash() {
        assertThat(masking.writeValueAsString(customer())).containsPattern("\"nationalId\":\"#[0-9a-f]{6}\"");
    }

    @Test
    void should_leave_out_a_dropped_property_entirely() {
        assertThat(masking.writeValueAsString(customer())).doesNotContain("passwordHash");
    }

    @Test
    void should_leave_an_unannotated_property_alone() {
        assertThat(masking.writeValueAsString(customer())).contains("\"city\":\"Nairobi\"");
    }

    @Test
    void should_leave_a_mapper_without_the_module_untouched() {
        ObjectMapper plain = JsonMapper.builder().build();

        assertThat(plain.writeValueAsString(customer())).contains("jane.wanjiru@acme.io");
    }

    public static class Shouty extends ValueSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(value.toUpperCase());
        }
    }

    public record Explicit(
            @Pii(strategy = MaskStrategy.PARTIAL)
            @JsonSerialize(using = Shouty.class)
            String email) {
    }

    @Test
    void should_mask_a_property_that_already_declares_its_own_serializer() {
        assertThat(masking.writeValueAsString(new Explicit("jane.wanjiru@acme.io")))
                .isEqualTo("{\"email\":\"j****@acme.io\"}");
    }
}
