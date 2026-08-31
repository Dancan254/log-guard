package io.github.dancan254.logguard.render;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.NestingConfig;
import io.github.dancan254.logguard.Pii;
import io.github.dancan254.logguard.mask.ValueMasker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class NestedRenderingTest {

    private static class Contact {
        @Pii(strategy = MaskStrategy.PARTIAL)
        String email = "jane.wanjiru@acme.io";
    }

    private static class Customer {
        Long id = 7L;
        Contact contact = new Contact();
    }

    private static class Node {
        @Pii
        String secret = "classified";
        Node next;
    }

    private static class Deep {
        @Pii
        String secret = "classified";
        Deep next;
    }

    private final ObjectRenderer renderer = new ObjectRenderer(new ValueMasker("pepper"), NestingConfig.DEFAULT);

    @Test
    void should_mask_field_of_nested_object_when_outer_class_has_no_annotation() {
        assertThat(renderer.render(new Customer()))
                .isEqualTo("Customer(id=7, contact=Contact(email=j****@acme.io))");
    }

    @Test
    void should_mask_elements_when_rendering_a_collection() {
        assertThat(renderer.render(List.of(new Contact(), new Contact())))
                .isEqualTo("[Contact(email=j****@acme.io), Contact(email=j****@acme.io)]");
    }

    @Test
    void should_mask_values_when_rendering_a_map() {
        Map<String, Contact> byName = new LinkedHashMap<>();
        byName.put("jane", new Contact());

        assertThat(renderer.render(byName)).isEqualTo("{jane=Contact(email=j****@acme.io)}");
    }

    @Test
    void should_mask_elements_when_rendering_an_array() {
        assertThat(renderer.render(new Contact[]{new Contact()}))
                .isEqualTo("[Contact(email=j****@acme.io)]");
    }

    @Test
    void should_cap_elements_when_collection_is_longer_than_the_limit() {
        List<Contact> contacts = IntStream.range(0, 13).mapToObj(index -> new Contact()).toList();

        assertThat(renderer.render(contacts)).endsWith("…(+3 more)]");
    }

    @Test
    void should_stop_at_the_depth_limit_when_objects_nest_deeper() {
        Deep root = new Deep();
        Deep current = root;
        for (int level = 0; level < 6; level++) {
            current.next = new Deep();
            current = current.next;
        }

        assertThat(renderer.render(root)).contains("<...>");
    }

    @Test
    void should_render_a_cycle_marker_when_an_object_references_itself() {
        Node node = new Node();
        node.next = node;

        assertThat(renderer.render(node)).isEqualTo("Node(secret=***, next=<cycle>)");
    }

    @Test
    void should_terminate_when_a_collection_contains_itself() {
        List<Object> self = new ArrayList<>();
        self.add(self);

        assertThat(renderer.render(self)).isEqualTo("[<cycle>]");
    }

    @Test
    void should_render_by_to_string_when_class_is_outside_the_configured_base_packages() {
        ObjectRenderer restricted = new ObjectRenderer(new ValueMasker("pepper"),
                new NestingConfig(3, 10, List.of("com.example")));

        assertThat(restricted.render(new Contact())).startsWith(Contact.class.getName() + "@");
    }
}
