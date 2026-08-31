package io.github.dancan254.logguard.autoconfigure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;
import io.github.dancan254.logguard.LogGuardMasker;
import io.github.dancan254.logguard.logback.MaskingInstaller;
import org.slf4j.LoggerFactory;

/** Every Logback reference lives here, so an application on Log4j2 never loads one. */
final class LogbackInstallation {

    private LogbackInstallation() {
    }

    static void install(LogGuardMasker masker) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return;
        }
        MaskingInstaller installer = new MaskingInstaller(masker);
        installer.install(context);
        if (context.getCopyOfListenerList().stream().noneMatch(ReinstallOnReset.class::isInstance)) {
            context.addListener(new ReinstallOnReset(installer));
        }
    }

    /** Boot resets the logger context more than once; every reset drops the wrappers. */
    private record ReinstallOnReset(MaskingInstaller installer) implements LoggerContextListener {

        @Override
        public boolean isResetResistant() {
            return true;
        }

        @Override
        public void onReset(LoggerContext context) {
            installer.install(context);
        }

        @Override
        public void onStart(LoggerContext context) {
        }

        @Override
        public void onStop(LoggerContext context) {
        }

        @Override
        public void onLevelChange(Logger logger, Level level) {
        }
    }
}
