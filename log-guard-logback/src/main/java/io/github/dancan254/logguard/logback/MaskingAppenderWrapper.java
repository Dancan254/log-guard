package io.github.dancan254.logguard.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.github.dancan254.logguard.LogGuardMasker;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MaskingAppenderWrapper extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final String NAME_PREFIX = "log-guard-";

    private final Appender<ILoggingEvent> delegate;
    private final LogGuardMasker masker;
    private final AtomicBoolean failureReported = new AtomicBoolean();

    public MaskingAppenderWrapper(Appender<ILoggingEvent> delegate, LogGuardMasker masker) {
        this.delegate = delegate;
        this.masker = masker;
        setContext(delegate.getContext());
        setName(NAME_PREFIX + delegate.getName());
    }

    public Appender<ILoggingEvent> getDelegate() {
        return delegate;
    }

    @Override
    protected void append(ILoggingEvent event) {
        delegate.doAppend(new MaskingLoggingEvent(event, masker, this::reportFailure));
    }

    @Override
    public void start() {
        if (!delegate.isStarted()) {
            delegate.start();
        }
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        delegate.stop();
    }

    /** Warn once: a masking failure repeats on every event and would drown the status log. */
    private void reportFailure(Throwable cause) {
        if (failureReported.compareAndSet(false, true)) {
            addWarn("log-guard could not mask an event for appender " + delegate.getName()
                    + "; its message was withheld. Further failures are not reported.", cause);
        }
    }
}
