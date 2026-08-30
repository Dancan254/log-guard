package io.github.dancan254.logguard.meta;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.Pii;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMetadataCacheTest {

    private static class Auditable {
        @Pii(strategy = MaskStrategy.HASH)
        String createdBy;
    }

    private static class Customer extends Auditable {
        static String tenant = "acme";
        Long id;
        @Pii
        String email;
    }

    private record Applicant(Long id, @Pii(strategy = MaskStrategy.PARTIAL) String phoneNumber) {
    }

    private class Membership {
        static String tier = "gold";
        @Pii
        String email;
    }

    private static class Anonymous {
        Long id;
        String reference;
    }

    @Test
    void should_find_annotated_fields_when_class_is_scanned() {
        PiiMetadata metadata = PiiMetadataCache.forClass(Customer.class);

        assertThat(metadata.fields())
                .filteredOn(PiiField::isMasked)
                .extracting(PiiField::name)
                .containsExactlyInAnyOrder("email", "createdBy");
    }

    @Test
    void should_include_inherited_fields_when_superclass_is_annotated() {
        PiiMetadata metadata = PiiMetadataCache.forClass(Customer.class);

        assertThat(metadata.fields())
                .extracting(PiiField::name)
                .containsExactly("createdBy", "id", "email");
    }

    @Test
    void should_ignore_static_and_synthetic_fields_when_scanning() {
        PiiMetadata metadata = PiiMetadataCache.forClass(Membership.class);

        assertThat(metadata.fields()).extracting(PiiField::name).containsExactly("email");
    }

    @Test
    void should_read_annotation_when_type_is_a_record() {
        PiiMetadata metadata = PiiMetadataCache.forClass(Applicant.class);

        assertThat(metadata.fields())
                .filteredOn(PiiField::isMasked)
                .singleElement()
                .satisfies(field -> {
                    assertThat(field.name()).isEqualTo("phoneNumber");
                    assertThat(field.strategy()).isEqualTo(MaskStrategy.PARTIAL);
                });
    }

    @Test
    void should_return_same_instance_when_scanned_twice() {
        assertThat(PiiMetadataCache.forClass(Customer.class))
                .isSameAs(PiiMetadataCache.forClass(Customer.class));
    }

    @Test
    void should_report_no_pii_when_class_has_no_annotations() {
        assertThat(PiiMetadataCache.forClass(Anonymous.class).hasPii()).isFalse();
    }
}
