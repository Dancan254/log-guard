package io.github.dancan254.logguard.demo.otel;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.github.dancan254.logguard.logback.MaskingAppenderWrapper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Boot's OTel starter exports signals but does not hand the SDK to the Logback appender, and
 * OpenTelemetryAppender.install() only inspects top-level appenders — so it cannot see one that
 * log-guard has wrapped. The SDK is handed over through the wrapper instead.
 */
@Configuration
@ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
public class OpenTelemetryLogbackBridge {

    private final OpenTelemetry openTelemetry;

    public OpenTelemetryLogbackBridge(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @EventListener(ApplicationReadyEvent.class)
    void install() {
        OpenTelemetryAppender.install(openTelemetry);
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext)) {
            return;
        }
        loggerContext.getLoggerList().forEach(logger ->
                logger.iteratorForAppenders().forEachRemaining(this::installIntoWrapped));
    }

    private void installIntoWrapped(Appender<ILoggingEvent> appender) {
        if (appender instanceof MaskingAppenderWrapper wrapper
                && wrapper.getDelegate() instanceof OpenTelemetryAppender otelAppender) {
            otelAppender.setOpenTelemetry(openTelemetry);
        }
    }
}
