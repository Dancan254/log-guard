package io.github.dancan254.logguard.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.github.dancan254.logguard.FailureMode;
import io.github.dancan254.logguard.LogGuardMasker;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.dancan254.logguard.logback.LogbackFixture.Customer;
import static io.github.dancan254.logguard.logback.LogbackFixture.capture;
import static io.github.dancan254.logguard.logback.LogbackFixture.captured;
import static io.github.dancan254.logguard.logback.LogbackFixture.defaultConfig;
import static io.github.dancan254.logguard.logback.LogbackFixture.masker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void should_report_masking_failed_when_message_masking_fails() {
        assertThat(new MaskingLoggingEvent(capture("mailing jane.wanjiru@acme.io now"),
                failingMessageMasker(), cause -> {}).isMaskingFailed()).isTrue();
    }

    @Test
    void should_report_masking_failed_when_mdc_masking_fails() {
        ILoggingEvent event = captured(Map.of("actor", "jane.wanjiru@acme.io"), logger -> logger.info("saved"));

        assertThat(new MaskingLoggingEvent(event, failingMdcMasker(), cause -> {}).isMaskingFailed()).isTrue();
    }

    @Test
    void should_report_masking_failed_when_key_value_pair_masking_fails() {
        ILoggingEvent event = captured(logger -> logger.atInfo().addKeyValue("customer", new Customer()).log("saved"));

        assertThat(new MaskingLoggingEvent(event, failingArgumentMasker(), cause -> {}).isMaskingFailed()).isTrue();
    }

    @Test
    void should_report_masking_failed_when_throwable_masking_fails() {
        ILoggingEvent event = captured(logger ->
                logger.error("failed", new IllegalStateException("jane.wanjiru@acme.io exists")));

        assertThat(new MaskingLoggingEvent(event, failingThrowableMasker(), cause -> {}).isMaskingFailed()).isTrue();
    }

    @Test
    void should_return_unmodifiable_mdc_copy_even_when_unchanged() {
        Map<String, String> mdc = new HashMap<>(Map.of("actor", "plain-name"));
        ILoggingEvent event = captured(mdc, logger -> logger.info("saved"));
        Map<String, String> masked = wrap(event).getMDCPropertyMap();

        assertThat(masked).containsEntry("actor", "plain-name");
        assertThatThrownBy(() -> masked.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_return_unmodifiable_key_value_pair_copy_even_when_unchanged() {
        ILoggingEvent event = captured(logger -> logger.atInfo().addKeyValue("actor", "plain-name").log("saved"));
        List<KeyValuePair> pairs = wrap(event).getKeyValuePairs();

        assertThat(pairs.getFirst().value).isEqualTo("plain-name");
        assertThatThrownBy(() -> pairs.add(new KeyValuePair("x", "y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static LogGuardMasker failingMessageMasker() {
        return new LogGuardMasker(defaultConfig(FailureMode.PLACEHOLDER)) {
            @Override
            public String maskMessage(String message) {
                throw new IllegalStateException("message masking blew up");
            }
        };
    }

    private static LogGuardMasker failingMdcMasker() {
        return new LogGuardMasker(defaultConfig(FailureMode.PLACEHOLDER)) {
            @Override
            public Map<String, String> maskMdc(Map<String, String> mdc) {
                throw new IllegalStateException("mdc masking blew up");
            }
        };
    }

    private static LogGuardMasker failingArgumentMasker() {
        return new LogGuardMasker(defaultConfig(FailureMode.PLACEHOLDER)) {
            @Override
            public Object maskArgument(Object argument) {
                throw new IllegalStateException("argument masking blew up");
            }
        };
    }

    private static LogGuardMasker failingThrowableMasker() {
        return new LogGuardMasker(defaultConfig(FailureMode.PLACEHOLDER)) {
            @Override
            public String maskMessage(String message) {
                if (message != null && message.contains("@acme.io")) {
                    throw new IllegalStateException("throwable masking blew up");
                }
                return super.maskMessage(message);
            }
        };
    }
}
