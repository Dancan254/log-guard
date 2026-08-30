package io.github.dancan254.logguard.logback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.AppenderAttachable;
import io.github.dancan254.logguard.LogGuardMasker;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class MaskingInstaller {

    private final LogGuardMasker masker;

    public MaskingInstaller(LogGuardMasker masker) {
        this.masker = masker;
    }

    public void install(LoggerContext context) {
        for (Logger logger : context.getLoggerList()) {
            wrapAttached(logger);
        }
    }

    /**
     * Wraps every appender a logger holds, including an AsyncAppender. Replacing an AsyncAppender's
     * own child is not possible: AsyncAppenderBase.detachAppender does not decrement the internal
     * appenderCount, so the one permitted child slot stays consumed and the replacement is refused.
     * Wrapping above it costs nothing, because MaskingLoggingEvent masks lazily and
     * prepareForDeferredProcessing does not force it — the work still lands on the async worker.
     */
    private void wrapAttached(AppenderAttachable<ILoggingEvent> attachable) {
        for (Appender<ILoggingEvent> appender : snapshot(attachable)) {
            if (appender instanceof MaskingAppenderWrapper) {
                continue;
            }
            attachable.detachAppender(appender);
            attachable.addAppender(started(new MaskingAppenderWrapper(appender, masker)));
        }
    }

    /** Detaching while iterating a logger's own appender list is a concurrent modification. */
    private static List<Appender<ILoggingEvent>> snapshot(AppenderAttachable<ILoggingEvent> attachable) {
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        for (Iterator<Appender<ILoggingEvent>> iterator = attachable.iteratorForAppenders(); iterator.hasNext(); ) {
            appenders.add(iterator.next());
        }
        return appenders;
    }

    private static MaskingAppenderWrapper started(MaskingAppenderWrapper wrapper) {
        wrapper.start();
        return wrapper;
    }
}
