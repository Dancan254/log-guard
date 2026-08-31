package io.github.dancan254.logguard.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static io.github.dancan254.logguard.logback.LogbackFixture.Customer;
import static io.github.dancan254.logguard.logback.LogbackFixture.captured;
import static io.github.dancan254.logguard.logback.LogbackFixture.masker;
import static org.assertj.core.api.Assertions.assertThat;

class MaskedChannelsTest {

    private static MaskingLoggingEvent wrap(ILoggingEvent event) {
        return new MaskingLoggingEvent(event, masker(), cause -> {
        });
    }

    @Test
    void should_mask_mdc_value_matched_by_a_pattern() {
        ILoggingEvent event = withMdc("actor", "jane.wanjiru@acme.io");

        assertThat(wrap(event).getMDCPropertyMap()).containsEntry("actor", "***");
    }

    @Test
    void should_redact_mdc_key_on_the_configured_list() {
        ILoggingEvent event = withMdc("customer-email", "Jane Wanjiru");

        assertThat(wrap(event).getMDCPropertyMap()).containsEntry("customer-email", "***");
    }

    @Test
    void should_expose_the_masked_map_through_the_deprecated_accessor() {
        ILoggingEvent event = withMdc("actor", "jane.wanjiru@acme.io");

        assertThat(wrap(event).getMdc()).containsEntry("actor", "***");
    }

    @Test
    void should_mask_a_string_key_value_pair_with_the_pattern_layer() {
        ILoggingEvent event = captured(logger ->
                logger.atInfo().addKeyValue("actor", "jane.wanjiru@acme.io").log("saved"));

        assertThat(wrap(event).getKeyValuePairs().getFirst().value).isEqualTo("***");
    }

    @Test
    void should_mask_an_object_key_value_pair_with_the_type_layer() {
        ILoggingEvent event = captured(logger ->
                logger.atInfo().addKeyValue("customer", new Customer()).log("saved"));

        assertThat(wrap(event).getKeyValuePairs().getFirst().value)
                .hasToString("Customer(id=42, email=j****@acme.io)");
    }

    @Test
    void should_mask_the_message_of_a_logged_exception() {
        ILoggingEvent event = captured(logger ->
                logger.error("failed", new IllegalStateException("jane.wanjiru@acme.io exists")));

        assertThat(wrap(event).getThrowableProxy().getMessage()).isEqualTo("*** exists");
    }

    @Test
    void should_mask_the_message_of_a_nested_cause() {
        Exception root = new IllegalStateException("jane.wanjiru@acme.io exists");
        ILoggingEvent event = captured(logger -> logger.error("failed", new RuntimeException("wrapped", root)));

        assertThat(wrap(event).getThrowableProxy().getCause().getMessage()).isEqualTo("*** exists");
    }

    @Test
    void should_mask_the_message_of_a_suppressed_exception() {
        Exception thrown = new IllegalStateException("outer");
        thrown.addSuppressed(new IllegalStateException("jane.wanjiru@acme.io exists"));
        ILoggingEvent event = captured(logger -> logger.error("failed", thrown));

        assertThat(wrap(event).getThrowableProxy().getSuppressed()[0].getMessage())
                .isEqualTo("*** exists");
    }

    @Test
    void should_leave_the_stack_trace_of_a_masked_exception_intact() {
        ILoggingEvent event = captured(logger ->
                logger.error("failed", new IllegalStateException("jane.wanjiru@acme.io exists")));

        IThrowableProxy proxy = wrap(event).getThrowableProxy();

        assertThat(proxy.getStackTraceElementProxyArray()).isNotEmpty();
    }

    private static ILoggingEvent withMdc(String key, String value) {
        return captured(Map.of(key, value), logger -> logger.info("saved"));
    }

    @Test
    void should_expose_a_masked_throwable_for_exporters_that_read_one() {
        ILoggingEvent event = captured(logger ->
                logger.error("failed", new IllegalStateException("jane.wanjiru@acme.io exists")));

        Throwable exported = ((ThrowableProxy) wrap(event).getThrowableProxy()).getThrowable();

        assertThat(exported).hasMessage("*** exists");
        assertThat(exported.getStackTrace()).isNotEmpty();
    }

    @Test
    void should_name_the_original_type_when_the_masked_throwable_is_printed() {
        ILoggingEvent event = captured(logger ->
                logger.error("failed", new IllegalStateException("jane.wanjiru@acme.io exists")));

        Throwable exported = ((ThrowableProxy) wrap(event).getThrowableProxy()).getThrowable();

        assertThat(exported).hasToString("java.lang.IllegalStateException: *** exists");
    }

    @Test
    void should_carry_the_masked_cause_into_the_exported_throwable() {
        Exception root = new IllegalStateException("jane.wanjiru@acme.io exists");
        ILoggingEvent event = captured(logger -> logger.error("failed", new RuntimeException("wrapped", root)));

        Throwable exported = ((ThrowableProxy) wrap(event).getThrowableProxy()).getThrowable();

        assertThat(exported.getCause()).hasMessage("*** exists");
    }

    @Test
    void should_hand_over_the_original_exception_when_its_chain_held_nothing_to_mask() {
        IllegalStateException thrown = new IllegalStateException("the pool was closed");
        ILoggingEvent event = captured(logger -> logger.error("failed", thrown));

        Throwable exported = ((ThrowableProxy) wrap(event).getThrowableProxy()).getThrowable();

        assertThat(exported).isSameAs(thrown);
    }
}
