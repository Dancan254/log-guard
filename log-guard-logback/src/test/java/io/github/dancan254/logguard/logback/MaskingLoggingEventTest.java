package io.github.dancan254.logguard.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

import static io.github.dancan254.logguard.logback.LogbackFixture.Customer;
import static io.github.dancan254.logguard.logback.LogbackFixture.capture;
import static io.github.dancan254.logguard.logback.LogbackFixture.masker;
import static org.assertj.core.api.Assertions.assertThat;

class MaskingLoggingEventTest {

    private static MaskingLoggingEvent wrap(ILoggingEvent event) {
        return new MaskingLoggingEvent(event, masker(), cause -> {
        });
    }

    @Test
    void should_mask_annotated_argument_when_event_is_wrapped() {
        MaskingLoggingEvent masked = wrap(capture("Processing customer {}", new Customer()));

        assertThat(masked.getArgumentArray()[0]).hasToString("Customer(id=42, email=j****@acme.io)");
    }

    @Test
    void should_reformat_message_from_masked_arguments_when_message_was_already_cached() {
        ILoggingEvent event = capture("Processing customer {}", new Customer());
        assertThat(event.getFormattedMessage()).contains("jane.wanjiru@acme.io");

        assertThat(wrap(event).getFormattedMessage())
                .isEqualTo("Processing customer Customer(id=42, email=j****@acme.io)");
    }

    @Test
    void should_mask_the_formatted_message_when_pii_was_concatenated_by_hand() {
        MaskingLoggingEvent masked = wrap(capture("mailing jane.wanjiru@acme.io now"));

        assertThat(masked.getFormattedMessage()).isEqualTo("mailing *** now");
    }

    @Test
    void should_delegate_logger_name_and_level_unchanged_when_wrapping() {
        ILoggingEvent event = capture("Processing customer {}", new Customer());

        MaskingLoggingEvent masked = wrap(event);

        assertThat(masked.getLoggerName()).isEqualTo("capture");
        assertThat(masked.getLevel()).isEqualTo(Level.INFO);
        assertThat(masked.getTimeStamp()).isEqualTo(event.getTimeStamp());
        assertThat(masked.getThreadName()).isEqualTo(event.getThreadName());
    }

    @Test
    void should_compute_masked_message_once_when_called_repeatedly() {
        MaskingLoggingEvent masked = wrap(capture("Processing customer {}", new Customer()));

        assertThat(masked.getFormattedMessage()).isSameAs(masked.getFormattedMessage());
    }

    @Test
    void should_not_double_mask_when_both_layers_are_enabled() {
        MaskingLoggingEvent masked = wrap(capture("Processing customer {}", new Customer()));

        assertThat(masked.getFormattedMessage()).endsWith("email=j****@acme.io)");
    }
}
