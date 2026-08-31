package io.github.dancan254.logguard.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.LogGuardMasker;
import io.github.dancan254.logguard.NestingConfig;
import io.github.dancan254.logguard.FailureMode;
import io.github.dancan254.logguard.MaskingConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.github.dancan254.logguard.logback.LogbackFixture.Customer;
import static io.github.dancan254.logguard.logback.LogbackFixture.capture;
import static io.github.dancan254.logguard.logback.LogbackFixture.listAppender;
import static io.github.dancan254.logguard.logback.LogbackFixture.masker;
import static org.assertj.core.api.Assertions.assertThat;

class MaskingAppenderWrapperTest {

    private static final LogGuardMasker THROWING = throwingMasker(FailureMode.PLACEHOLDER);

    private static LogGuardMasker throwingMasker(FailureMode onFailure) {
        return new LogGuardMasker(
                new MaskingConfig(true, true, List.of(BuiltInPattern.EMAIL), List.of(), "pepper",
                        Set.of(), NestingConfig.DEFAULT, onFailure)) {
            @Override
            public Object maskArgument(Object argument) {
                throw new IllegalStateException("masking blew up");
            }
        };
    }

    private final LoggerContext context = LogbackFixture.loggerContext();

    @Test
    void should_pass_masked_event_to_delegate_when_appending() {
        ListAppender<ILoggingEvent> delegate = listAppender(context, "console");
        MaskingAppenderWrapper wrapper = new MaskingAppenderWrapper(delegate, masker());
        wrapper.start();

        wrapper.doAppend(capture("Processing customer {}", new Customer()));

        assertThat(delegate.list).singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .isEqualTo("Processing customer Customer(id=42, email=j****@acme.io)");
    }

    @Test
    void should_start_delegate_when_wrapper_starts() {
        ListAppender<ILoggingEvent> delegate = new ListAppender<>();
        delegate.setContext(context);
        delegate.setName("console");

        new MaskingAppenderWrapper(delegate, masker()).start();

        assertThat(delegate.isStarted()).isTrue();
    }

    @Test
    void should_stop_delegate_when_wrapper_stops() {
        ListAppender<ILoggingEvent> delegate = listAppender(context, "console");
        MaskingAppenderWrapper wrapper = new MaskingAppenderWrapper(delegate, masker());
        wrapper.start();

        wrapper.stop();

        assertThat(delegate.isStarted()).isFalse();
    }

    @Test
    void should_emit_placeholder_message_when_masking_fails() {
        ListAppender<ILoggingEvent> delegate = listAppender(context, "console");
        MaskingAppenderWrapper wrapper = new MaskingAppenderWrapper(delegate, THROWING);
        wrapper.start();

        wrapper.doAppend(capture("Processing customer {}", new Customer()));

        assertThat(delegate.list).singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .isEqualTo(MaskingLoggingEvent.MASKING_FAILED_MESSAGE);
    }

    @Test
    void should_keep_level_and_logger_when_masking_fails() {
        ListAppender<ILoggingEvent> delegate = listAppender(context, "console");
        MaskingAppenderWrapper wrapper = new MaskingAppenderWrapper(delegate, THROWING);
        wrapper.start();

        wrapper.doAppend(capture("Processing customer {}", new Customer()));

        assertThat(delegate.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getLoggerName()).isEqualTo("capture");
        });
    }

    @Test
    void should_discard_the_event_when_masking_fails_and_the_mode_is_drop() {
        ListAppender<ILoggingEvent> delegate = listAppender(context, "console");
        MaskingAppenderWrapper wrapper =
                new MaskingAppenderWrapper(delegate, throwingMasker(FailureMode.DROP));
        wrapper.start();

        wrapper.doAppend(capture("Processing customer {}", new Customer()));

        assertThat(delegate.list).isEmpty();
    }

    @Test
    void should_emit_the_raw_payload_when_masking_fails_and_the_mode_is_passthrough() {
        ListAppender<ILoggingEvent> delegate = listAppender(context, "console");
        MaskingAppenderWrapper wrapper =
                new MaskingAppenderWrapper(delegate, throwingMasker(FailureMode.PASSTHROUGH));
        wrapper.start();

        wrapper.doAppend(capture("Processing customer {}", new Customer()));

        assertThat(delegate.list).singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .asString()
                .contains("jane.wanjiru@acme.io");
    }

    @Test
    void should_keep_appending_when_masking_fails_and_the_mode_is_placeholder() {
        ListAppender<ILoggingEvent> delegate = listAppender(context, "console");
        MaskingAppenderWrapper wrapper = new MaskingAppenderWrapper(delegate, THROWING);
        wrapper.start();

        wrapper.doAppend(capture("Processing customer {}", new Customer()));

        assertThat(delegate.list).singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .isEqualTo(MaskingLoggingEvent.MASKING_FAILED_MESSAGE);
    }
}
