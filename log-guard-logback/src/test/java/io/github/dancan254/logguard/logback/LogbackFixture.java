package io.github.dancan254.logguard.logback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.LogGuardMasker;
import io.github.dancan254.logguard.NestingConfig;
import io.github.dancan254.logguard.FailureMode;
import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.MaskingConfig;
import io.github.dancan254.logguard.Pii;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.Set;

final class LogbackFixture {

    static class Customer {
        Long id = 42L;
        @Pii(strategy = MaskStrategy.PARTIAL)
        String email = "jane.wanjiru@acme.io";

        @Override
        public String toString() {
            return "Customer(id=" + id + ", email=" + email + ")";
        }
    }

    private LogbackFixture() {
    }

    /** A hand-built context has no MDC adapter; the SLF4J provider installs one in a real app. */
    static LoggerContext loggerContext() {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        return context;
    }

    static LogGuardMasker masker() {
        return masker(FailureMode.PLACEHOLDER);
    }

    static LogGuardMasker masker(FailureMode onFailure) {
        return new LogGuardMasker(new MaskingConfig(true, true,
                List.of(BuiltInPattern.EMAIL), List.of(), "pepper",
                Set.of("customer-email"), NestingConfig.DEFAULT, onFailure));
    }

    static ListAppender<ILoggingEvent> listAppender(LoggerContext context, String name) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.setName(name);
        appender.start();
        return appender;
    }

    static ILoggingEvent capture(String message, Object... arguments) {
        return captured(logger -> logger.info(message, arguments));
    }

    static ILoggingEvent captured(Consumer<Logger> logging) {
        return captured(Map.of(), logging);
    }

    /** MDC has to go through this context's own adapter; the global one belongs to another. */
    static ILoggingEvent captured(Map<String, String> mdc, Consumer<Logger> logging) {
        LoggerContext context = loggerContext();
        mdc.forEach(context.getMDCAdapter()::put);
        Logger logger = context.getLogger("capture");
        ListAppender<ILoggingEvent> appender = listAppender(context, "capture");
        logger.addAppender(appender);
        logging.accept(logger);
        return appender.list.getFirst();
    }
}
