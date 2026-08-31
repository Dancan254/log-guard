package io.github.dancan254.logguard.autoconfigure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;
import io.github.dancan254.logguard.LogGuardMasker;
import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.MaskingConfig;
import io.github.dancan254.logguard.exception.MissingHashSaltException;
import io.github.dancan254.logguard.logback.MaskingInstaller;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import java.util.Set;

final class LogGuardInstallation {

    private LogGuardInstallation() {
    }

    static LogGuardMasker apply(LogGuardProperties properties) {
        LogGuardMasker masker = new LogGuardMasker(toMaskingConfig(properties));
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            install(context, masker);
        }
        return masker;
    }

    private static void install(LoggerContext context, LogGuardMasker masker) {
        MaskingInstaller installer = new MaskingInstaller(masker);
        installer.install(context);
        if (context.getCopyOfListenerList().stream().noneMatch(ReinstallOnReset.class::isInstance)) {
            context.addListener(new ReinstallOnReset(installer));
        }
    }

    static MaskingConfig toMaskingConfig(LogGuardProperties properties) {
        var patterns = properties.patterns();
        var custom = patterns.custom().stream()
                .map(pattern -> new MaskingConfig.CustomPattern(pattern.name(), pattern.regex(), pattern.strategy()))
                .toList();
        requireHashSalt(properties, custom);
        return new MaskingConfig(properties.typeAware().enabled(), patterns.enabled(),
                patterns.builtIn(), custom, properties.hashSalt(),
                Set.copyOf(properties.mdc().redactKeys()),
                properties.nesting().toNestingConfig(),
                properties.onFailure());
    }

    /**
     * Only custom patterns can be checked here. A {@code @Pii(strategy = HASH)} on a class nobody
     * has loaded yet needs the classpath scan the startup validator brings.
     */
    private static void requireHashSalt(LogGuardProperties properties, java.util.List<MaskingConfig.CustomPattern> custom) {
        boolean salted = properties.hashSalt() != null && !properties.hashSalt().isBlank();
        if (!salted && custom.stream().anyMatch(pattern -> pattern.strategy() == MaskStrategy.HASH)) {
            throw new MissingHashSaltException();
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
