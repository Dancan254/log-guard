package io.github.dancan254.logguard.log4j2;

import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.FailureMode;
import io.github.dancan254.logguard.LogGuardMasker;
import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.MaskingConfig;
import io.github.dancan254.logguard.NestingConfig;
import io.github.dancan254.logguard.Pii;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogGuardRewritePolicyTest {

    static class Customer {
        Long id = 42L;
        @Pii(strategy = MaskStrategy.PARTIAL)
        String email = "jane.wanjiru@acme.io";

        @Override
        public String toString() {
            return "Customer(id=" + id + ", email=" + email + ")";
        }
    }

    private static final LogGuardMasker MASKER = new LogGuardMasker(new MaskingConfig(true, true,
            List.of(BuiltInPattern.EMAIL), List.of(), "pepper",
            Set.of("customer-email"), NestingConfig.DEFAULT, FailureMode.PLACEHOLDER,
            MaskingConfig.DEFAULT_MAX_MESSAGE_LENGTH));

    private final LogGuardRewritePolicy policy = LogGuardRewritePolicy.using(MASKER);

    private static Log4jLogEvent.Builder event() {
        return Log4jLogEvent.newBuilder()
                .setLoggerName("test")
                .setLevel(Level.INFO);
    }

    @Test
    void should_mask_an_annotated_argument() {
        LogEvent source = event()
                .setMessage(new ParameterizedMessage("Processing customer {}", new Customer()))
                .build();

        assertThat(policy.rewrite(source).getMessage().getFormattedMessage())
                .isEqualTo("Processing customer Customer(id=42, email=j****@acme.io)");
    }

    @Test
    void should_mask_pii_concatenated_into_the_message() {
        LogEvent source = event().setMessage(new SimpleMessage("mailing jane.wanjiru@acme.io now")).build();

        assertThat(policy.rewrite(source).getMessage().getFormattedMessage())
                .isEqualTo("mailing *** now");
    }

    @Test
    void should_mask_a_context_map_value() {
        StringMap context = new SortedArrayStringMap();
        context.putValue("actor", "jane.wanjiru@acme.io");
        LogEvent source = event().setMessage(new SimpleMessage("saved")).setContextData(context).build();

        assertThat(policy.rewrite(source).getContextData().<String>getValue("actor")).isEqualTo("***");
    }

    @Test
    void should_redact_a_context_key_on_the_configured_list() {
        StringMap context = new SortedArrayStringMap();
        context.putValue("customer-email", "Jane Wanjiru");
        LogEvent source = event().setMessage(new SimpleMessage("saved")).setContextData(context).build();

        assertThat(policy.rewrite(source).getContextData().<String>getValue("customer-email")).isEqualTo("***");
    }

    @Test
    void should_mask_the_message_of_a_thrown_exception() {
        LogEvent source = event()
                .setMessage(new SimpleMessage("failed"))
                .setThrown(new IllegalStateException("jane.wanjiru@acme.io exists"))
                .build();

        assertThat(policy.rewrite(source).getThrown()).hasMessage("*** exists");
    }

    @Test
    void should_mask_the_message_of_a_nested_cause() {
        Exception root = new IllegalStateException("jane.wanjiru@acme.io exists");
        LogEvent source = event()
                .setMessage(new SimpleMessage("failed"))
                .setThrown(new RuntimeException("wrapped", root))
                .build();

        assertThat(policy.rewrite(source).getThrown().getCause()).hasMessage("*** exists");
    }

    @Test
    void should_name_the_original_type_when_a_masked_exception_is_printed() {
        LogEvent source = event()
                .setMessage(new SimpleMessage("failed"))
                .setThrown(new IllegalStateException("jane.wanjiru@acme.io exists"))
                .build();

        assertThat(policy.rewrite(source).getThrown())
                .hasToString("java.lang.IllegalStateException: *** exists");
    }

    @Test
    void should_hand_back_the_original_exception_when_nothing_needed_masking() {
        IllegalStateException thrown = new IllegalStateException("the pool was closed");
        LogEvent source = event().setMessage(new SimpleMessage("failed")).setThrown(thrown).build();

        assertThat(policy.rewrite(source).getThrown()).isSameAs(thrown);
    }

    @Test
    void should_return_the_same_event_when_there_was_nothing_to_mask() {
        LogEvent source = event().setMessage(new SimpleMessage("nothing to see here")).build();

        assertThat(policy.rewrite(source)).isSameAs(source);
    }

    @Test
    void should_keep_the_level_and_logger_of_the_event_it_rewrote() {
        LogEvent source = event().setMessage(new SimpleMessage("mailing jane.wanjiru@acme.io")).build();

        LogEvent rewritten = policy.rewrite(source);

        assertThat(rewritten.getLoggerName()).isEqualTo("test");
        assertThat(rewritten.getLevel()).isEqualTo(Level.INFO);
    }

    /** Masking cannot start without a formatted message, so this fails the whole rewrite. */
    static class ExplodingMessage implements Message {

        @Override
        public String getFormattedMessage() {
            throw new IllegalStateException("formatting blew up");
        }

        @Override
        public String getFormat() {
            return "customer {}";
        }

        @Override
        public Object[] getParameters() {
            return new Object[]{"jane.wanjiru@acme.io"};
        }

        @Override
        public Throwable getThrowable() {
            return null;
        }
    }

    private static LogGuardRewritePolicy policyFailing(FailureMode mode) {
        return LogGuardRewritePolicy.using(new LogGuardMasker(new MaskingConfig(true, true,
                List.of(BuiltInPattern.EMAIL), List.of(), "pepper", Set.of(),
                NestingConfig.DEFAULT, mode, MaskingConfig.DEFAULT_MAX_MESSAGE_LENGTH)));
    }

    private static LogEvent explodingEvent() {
        return event().setMessage(new ExplodingMessage()).build();
    }

    @Test
    void should_withhold_the_payload_when_masking_fails_and_mode_is_placeholder() {
        LogEvent rewritten = policyFailing(FailureMode.PLACEHOLDER).rewrite(explodingEvent());

        assertThat(rewritten.getMessage().getFormattedMessage())
                .isEqualTo(LogGuardRewritePolicy.MASKING_FAILED_MESSAGE);
    }

    @Test
    void should_return_the_original_event_when_masking_fails_and_mode_is_passthrough() {
        LogEvent source = explodingEvent();

        assertThat(policyFailing(FailureMode.PASSTHROUGH).rewrite(source)).isSameAs(source);
    }

    @Test
    void should_let_log4j2_discard_the_event_when_masking_fails_and_mode_is_drop() {
        LogGuardRewritePolicy policy = policyFailing(FailureMode.DROP);
        LogEvent source = explodingEvent();

        assertThatThrownBy(() -> policy.rewrite(source)).isInstanceOf(RuntimeException.class);
    }
}
