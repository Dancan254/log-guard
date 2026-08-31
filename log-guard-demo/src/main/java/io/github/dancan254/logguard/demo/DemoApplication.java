package io.github.dancan254.logguard.demo;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.github.dancan254.logguard.logback.MaskingAppenderWrapper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    /**
     * Boot's OTel starter exports signals but does not hand the SDK to the Logback appender.
     * OpenTelemetryAppender.install() only inspects top-level appenders, so it cannot see one
     * that log-guard has wrapped — the SDK is handed over through the wrapper instead.
     */
    @Component
    static class OpenTelemetryAppenderInitializer {

        private final OpenTelemetry openTelemetry;

        OpenTelemetryAppenderInitializer(OpenTelemetry openTelemetry) {
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
}
