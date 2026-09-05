package io.github.dancan254.logguard.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import io.github.dancan254.logguard.FailureMode;
import io.github.dancan254.logguard.LogGuardMasker;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.helpers.MessageFormatter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MaskingLoggingEvent implements ILoggingEvent {

    static final String MASKING_FAILED_MESSAGE =
            "[log-guard] masking failed for this event, payload withheld";

    private static final Object[] NO_ARGUMENTS = new Object[0];

    private final ILoggingEvent delegate;
    private final LogGuardMasker masker;
    private final FailureMode onFailure;
    private final Consumer<Throwable> failureReporter;

    // Unsynchronised on purpose: a wrapper builds one of these per doAppend, and Logback hands an
    // event to a single appender at a time, so only one thread ever reads or writes these.
    private boolean masked;
    private boolean maskingFailed;
    private Object[] maskedArguments;
    private String maskedMessage;
    private Map<String, String> maskedMdc;
    private List<KeyValuePair> maskedKeyValuePairs;
    private IThrowableProxy maskedThrowableProxy;
    private boolean throwableResolved;

    public MaskingLoggingEvent(ILoggingEvent delegate, LogGuardMasker masker,
                               Consumer<Throwable> failureReporter) {
        this(delegate, masker, masker.onFailure(), failureReporter);
    }

    public MaskingLoggingEvent(ILoggingEvent delegate, LogGuardMasker masker,
                               FailureMode onFailure, Consumer<Throwable> failureReporter) {
        this.delegate = delegate;
        this.masker = masker;
        this.onFailure = onFailure;
        this.failureReporter = failureReporter;
    }

    /** Forces the message channel, so an appender configured to drop can decide before appending. */
    boolean isMaskingFailed() {
        mask();
        return maskingFailed;
    }

    @Override
    public Object[] getArgumentArray() {
        mask();
        return maskedArguments;
    }

    @Override
    public String getFormattedMessage() {
        mask();
        return maskedMessage;
    }

    private void mask() {
        if (masked) {
            return;
        }
        masked = true;
        try {
            Object[] arguments = delegate.getArgumentArray();
            maskedArguments = maskArguments(arguments);
            maskedMessage = masker.maskMessage(format(delegate.getMessage(), maskedArguments));
        } catch (RuntimeException | LinkageError cause) {
            maskingFailed = true;
            if (onFailure == FailureMode.PASSTHROUGH) {
                maskedArguments = delegate.getArgumentArray();
                maskedMessage = delegate.getFormattedMessage();
            } else {
                // Neither dropping the payload nor letting it through raw is free: one loses the
                // incident you are debugging, the other is the leak this library exists to prevent.
                maskedArguments = NO_ARGUMENTS;
                maskedMessage = MASKING_FAILED_MESSAGE;
            }
            failureReporter.accept(cause);
        }
    }

    private Object[] maskArguments(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return arguments;
        }
        Object[] replaced = null;
        for (int index = 0; index < arguments.length; index++) {
            Object masked = masker.maskArgument(arguments[index]);
            if (masked != arguments[index]) {
                if (replaced == null) {
                    replaced = arguments.clone();
                }
                replaced[index] = masked;
            }
        }
        return replaced == null ? arguments : replaced;
    }

    /**
     * The message is rebuilt from the masked arguments rather than read off the delegate:
     * prepareForDeferredProcessing has already formatted and cached the raw one by the time this
     * wrapper runs behind an AsyncAppender.
     */
    private static String format(String message, Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return message;
        }
        return MessageFormatter.arrayFormat(message, arguments).getMessage();
    }

    /**
     * Deliberately does not mask. AsyncAppender calls this on the thread that made the log call,
     * so masking stays lazy and its reflection and regex run on the async worker instead.
     */
    @Override
    public void prepareForDeferredProcessing() {
        delegate.prepareForDeferredProcessing();
    }

    @Override
    public String getThreadName() {
        return delegate.getThreadName();
    }

    @Override
    public Level getLevel() {
        return delegate.getLevel();
    }

    @Override
    public String getMessage() {
        return delegate.getMessage();
    }

    @Override
    public String getLoggerName() {
        return delegate.getLoggerName();
    }

    @Override
    public LoggerContextVO getLoggerContextVO() {
        return delegate.getLoggerContextVO();
    }

    @Override
    public IThrowableProxy getThrowableProxy() {
        if (!throwableResolved) {
            throwableResolved = true;
            IThrowableProxy original = delegate.getThrowableProxy();
            maskedThrowableProxy = original == null
                    ? null
                    : new MaskingThrowableProxy(original, masker, failureReporter);
        }
        return maskedThrowableProxy;
    }

    @Override
    public StackTraceElement[] getCallerData() {
        return delegate.getCallerData();
    }

    @Override
    public boolean hasCallerData() {
        return delegate.hasCallerData();
    }

    @Override
    @SuppressWarnings("deprecation")
    public Marker getMarker() {
        return delegate.getMarker();
    }

    @Override
    public List<Marker> getMarkerList() {
        return delegate.getMarkerList();
    }

    /**
     * An MDC value outlives the log call that set it, so one unmasked put leaks into every
     * subsequent line on that thread.
     */
    @Override
    public Map<String, String> getMDCPropertyMap() {
        if (maskedMdc == null) {
            maskedMdc = maskChannel(() -> masker.maskMdc(delegate.getMDCPropertyMap()),
                    delegate::getMDCPropertyMap, Map.of());
        }
        return maskedMdc;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Map<String, String> getMdc() {
        return getMDCPropertyMap();
    }

    @Override
    public long getTimeStamp() {
        return delegate.getTimeStamp();
    }

    @Override
    public int getNanoseconds() {
        return delegate.getNanoseconds();
    }

    @Override
    public Instant getInstant() {
        return delegate.getInstant();
    }

    @Override
    public long getSequenceNumber() {
        return delegate.getSequenceNumber();
    }

    /** Object values go through the type layer, string values through the pattern layer. */
    @Override
    public List<KeyValuePair> getKeyValuePairs() {
        if (maskedKeyValuePairs == null) {
            maskedKeyValuePairs = maskChannel(this::maskKeyValuePairs,
                    delegate::getKeyValuePairs, List.of());
        }
        return maskedKeyValuePairs;
    }

    private List<KeyValuePair> maskKeyValuePairs() {
        List<KeyValuePair> pairs = delegate.getKeyValuePairs();
        if (pairs == null || pairs.isEmpty()) {
            return pairs;
        }
        List<KeyValuePair> replaced = null;
        for (int index = 0; index < pairs.size(); index++) {
            KeyValuePair pair = pairs.get(index);
            Object masked = maskKeyValue(pair.value);
            if (masked != pair.value) {
                if (replaced == null) {
                    replaced = new ArrayList<>(pairs);
                }
                replaced.set(index, new KeyValuePair(pair.key, masked));
            }
        }
        return replaced == null ? pairs : replaced;
    }

    private Object maskKeyValue(Object value) {
        if (value instanceof CharSequence text) {
            return masker.maskMessage(text.toString());
        }
        return masker.maskArgument(value);
    }

    /**
     * Every channel fails the same way: the mode decides between the raw value and an empty one,
     * and the failure is reported once either way.
     */
    private <T> T maskChannel(Supplier<T> masking, Supplier<T> raw, T empty) {
        try {
            return masking.get();
        } catch (RuntimeException | LinkageError cause) {
            maskingFailed = true;
            failureReporter.accept(cause);
            return onFailure == FailureMode.PASSTHROUGH ? raw.get() : empty;
        }
    }
}
