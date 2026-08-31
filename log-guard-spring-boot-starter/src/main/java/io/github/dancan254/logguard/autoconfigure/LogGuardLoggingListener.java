package io.github.dancan254.logguard.autoconfigure;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/**
 * Installs masking as early as Boot allows: one step after LoggingApplicationListener has
 * configured the logging system, and long before any bean exists. Anything logged before that —
 * the banner and Boot's own startup lines — is outside our reach, and the README says so.
 */
public class LogGuardLoggingListener
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final int AFTER_LOGGING_APPLICATION_LISTENER = Ordered.HIGHEST_PRECEDENCE + 21;

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        LogGuardProperties properties = Binder.get(event.getEnvironment())
                .bindOrCreate("log-guard", LogGuardProperties.class);
        if (!properties.enabled()) {
            return;
        }
        LogGuardInstallation.apply(properties);
    }

    @Override
    public int getOrder() {
        return AFTER_LOGGING_APPLICATION_LISTENER;
    }
}
