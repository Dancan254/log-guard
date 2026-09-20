package io.github.dancan254.logguard.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.FilterReply;
import io.github.dancan254.logguard.LogGuardMasker;
import org.slf4j.Marker;

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
        if (context.getTurboFilterList().stream().noneMatch(DynamicAppenderWrapper.class::isInstance)) {
            DynamicAppenderWrapper turboFilter = new DynamicAppenderWrapper(this);
            turboFilter.start();
            context.addTurboFilter(turboFilter);
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
        synchronized (attachable) {
            for (Appender<ILoggingEvent> appender : snapshot(attachable)) {
                if (appender instanceof MaskingAppenderWrapper) {
                    continue;
                }
                attachable.detachAppender(appender);
                attachable.addAppender(started(new MaskingAppenderWrapper(appender, masker)));
            }
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

    private static boolean hasUnwrappedAppender(AppenderAttachable<ILoggingEvent> attachable) {
        for (Iterator<Appender<ILoggingEvent>> iterator = attachable.iteratorForAppenders(); iterator.hasNext(); ) {
            if (!(iterator.next() instanceof MaskingAppenderWrapper)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Logback has no listener for appender attachment, so a turbo filter lazily wraps any appender
     * added after installation the next time the logger is used. The check walks from the logging
     * logger up to the root so appenders attached to any ancestor are also wrapped.
     */
    private static final class DynamicAppenderWrapper extends TurboFilter {

        private final MaskingInstaller installer;

        DynamicAppenderWrapper(MaskingInstaller installer) {
            this.installer = installer;
        }

        @Override
        public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
            LoggerContext context = logger.getLoggerContext();
            String name = logger.getName();
            while (true) {
                Logger current = context.getLogger(name);
                if (hasUnwrappedAppender(current)) {
                    installer.wrapAttached(current);
                }
                if (name.isEmpty()) {
                    break;
                }
                int dot = name.lastIndexOf('.');
                name = (dot == -1) ? "" : name.substring(0, dot);
            }
            return FilterReply.NEUTRAL;
        }
    }
}
