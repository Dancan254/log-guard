package io.github.dancan254.logguard.log4j2;

import io.github.dancan254.logguard.FailureMode;
import io.github.dancan254.logguard.LogGuardMasker;
import io.github.dancan254.logguard.mask.ThrowableMasker;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;

import java.util.Map;

/**
 * Log4j2's rewrite point is a different shape from Logback's appender wrapping — it hands over a
 * finished event and takes a replacement — so the adapter differs while the engine does not.
 */
@Plugin(name = "LogGuardRewritePolicy", category = Core.CATEGORY_NAME,
        elementType = "rewritePolicy", printObject = true)
public final class LogGuardRewritePolicy implements RewritePolicy {

    static final String MASKING_FAILED_MESSAGE =
            "[log-guard] masking failed for this event, payload withheld";

    private final LogGuardMasker fixedMasker;

    private LogGuardRewritePolicy(LogGuardMasker fixedMasker) {
        this.fixedMasker = fixedMasker;
    }

    /**
     * Log4j2 reads its configuration before Spring's listener runs, so the policy cannot be handed
     * a masker at build time. It resolves one per event instead, and until the starter publishes
     * it, events pass through — the same window the Logback adapter has for Boot's own banner.
     */
    @PluginFactory
    public static LogGuardRewritePolicy createPolicy() {
        return new LogGuardRewritePolicy(null);
    }

    public static LogGuardRewritePolicy using(LogGuardMasker masker) {
        return new LogGuardRewritePolicy(masker);
    }

    @Override
    public LogEvent rewrite(LogEvent source) {
        LogGuardMasker masker = fixedMasker == null ? LogGuardMaskerHolder.get() : fixedMasker;
        if (masker == null) {
            return source;
        }
        try {
            return rewrite(source, masker);
        } catch (RuntimeException | LinkageError cause) {
            return onMaskingFailure(source, masker, cause);
        }
    }

    /**
     * The rewrite point has no way to discard an event — RewriteAppender passes whatever comes back
     * straight to its appenders — so DROP rethrows and lets Log4j2 discard the event itself.
     */
    private LogEvent onMaskingFailure(LogEvent source, LogGuardMasker masker, Throwable cause) {
        StatusLogger.getLogger().warn("log-guard could not mask an event", cause);
        if (masker.onFailure() == FailureMode.PASSTHROUGH) {
            return source;
        }
        if (masker.onFailure() == FailureMode.DROP) {
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw (LinkageError) cause;
        }
        // Built field by field rather than from the source event: Log4jLogEvent.Builder(LogEvent)
        // formats the source message, which is one of the things that can have just thrown. The
        // context map is left behind with the payload, since it is a channel that carries PII too.
        return Log4jLogEvent.newBuilder()
                .setLoggerName(source.getLoggerName())
                .setLoggerFqcn(source.getLoggerFqcn())
                .setLevel(source.getLevel())
                .setMarker(source.getMarker())
                .setMessage(new SimpleMessage(MASKING_FAILED_MESSAGE))
                .setTimeMillis(source.getTimeMillis())
                .setThreadName(source.getThreadName())
                .build();
    }

    private LogEvent rewrite(LogEvent source, LogGuardMasker masker) {
        Message maskedMessage = maskMessage(source.getMessage(), masker);
        StringMap maskedContext = maskContext(source.getContextData(), masker);
        Throwable maskedThrown = ThrowableMasker.mask(source.getThrown(), masker);

        if (maskedMessage == source.getMessage() && maskedContext == null
                && maskedThrown == source.getThrown()) {
            return source;
        }
        Log4jLogEvent.Builder builder = new Log4jLogEvent.Builder(source)
                .setMessage(maskedMessage)
                .setThrown(maskedThrown)
                // The proxy caches the original message; dropping it makes Log4j2 rebuild from
                // the masked throwable rather than print the one it already formatted.
                .setThrownProxy(null);
        if (maskedContext != null) {
            builder.setContextData(maskedContext);
        }
        return builder.build();
    }

    /**
     * The formatted text is masked rather than the parameters alone, so that PII a developer
     * concatenated into the format string is caught too — the same guarantee the Logback adapter
     * gives. A structured layout therefore sees one masked string rather than format-plus-arguments.
     */
    private Message maskMessage(Message message, LogGuardMasker masker) {
        if (message == null) {
            return null;
        }
        String formatted = message.getFormattedMessage();
        String masked = masker.maskMessage(formatWithMaskedParameters(message, formatted, masker));
        return masked.equals(formatted) ? message : new SimpleMessage(masked);
    }

    private String formatWithMaskedParameters(Message message, String formatted, LogGuardMasker masker) {
        Object[] parameters = message.getParameters();
        if (parameters == null || parameters.length == 0) {
            return formatted;
        }
        Object[] masked = null;
        for (int index = 0; index < parameters.length; index++) {
            Object replacement = masker.maskArgument(parameters[index]);
            if (replacement != parameters[index]) {
                if (masked == null) {
                    masked = parameters.clone();
                }
                masked[index] = replacement;
            }
        }
        if (masked == null) {
            return formatted;
        }
        String format = message.getFormat();
        // Only a {}-style format can be rebuilt this way. Handing ParameterizedMessage.format a
        // printf or MessageFormat pattern returns it verbatim and every argument disappears from
        // the line, so those keep their own rendering and the pattern layer alone.
        if (format == null || !format.contains("{}")) {
            return formatted;
        }
        return ParameterizedMessage.format(format, masked);
    }

    /** Returns null when no value changed, so an untouched event keeps its own context map. */
    private StringMap maskContext(ReadOnlyStringMap contextData, LogGuardMasker masker) {
        if (contextData == null || contextData.isEmpty()) {
            return null;
        }
        Map<String, String> original = contextData.toMap();
        Map<String, String> masked = masker.maskMdc(original);
        if (masked == original) {
            return null;
        }
        StringMap replacement = new SortedArrayStringMap(masked.size());
        masked.forEach(replacement::putValue);
        return replacement;
    }
}
