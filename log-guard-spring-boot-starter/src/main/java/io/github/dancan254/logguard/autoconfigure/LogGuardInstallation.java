package io.github.dancan254.logguard.autoconfigure;

import io.github.dancan254.logguard.LogGuardMasker;
import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.MaskingConfig;
import io.github.dancan254.logguard.exception.MissingHashSaltException;
import org.springframework.util.ClassUtils;

import java.util.Set;

final class LogGuardInstallation {

    private LogGuardInstallation() {
    }

    private static final ClassLoader CLASS_LOADER = LogGuardInstallation.class.getClassLoader();

    private static final String LOGBACK_CONTEXT = "ch.qos.logback.classic.LoggerContext";

    private static final String LOG4J2_POLICY =
            "io.github.dancan254.logguard.log4j2.LogGuardMaskerHolder";

    static LogGuardMasker apply(LogGuardProperties properties) {
        LogGuardMasker masker = new LogGuardMasker(toMaskingConfig(properties));
        if (ClassUtils.isPresent(LOGBACK_CONTEXT, CLASS_LOADER)) {
            LogbackInstallation.install(masker);
        }
        publishToLog4j2(masker);
        return masker;
    }

    /**
     * Log4j2 builds its own plugins from configuration, so the adapter cannot be handed the masker
     * — it is published where the rewrite policy will look for it. Wiring the policy itself stays
     * the application's job, in log4j2.xml, because that is where its appender graph is described.
     */
    private static void publishToLog4j2(LogGuardMasker masker) {
        try {
            Class<?> holder = Class.forName(LOG4J2_POLICY, true, CLASS_LOADER);
            holder.getMethod("set", LogGuardMasker.class).invoke(null, masker);
        } catch (ClassNotFoundException | LinkageError absent) {
            // The app is on Logback. Nothing to publish.
        } catch (ReflectiveOperationException cause) {
            throw new IllegalStateException("log-guard could not publish its masker to Log4j2", cause);
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
                properties.onFailure(),
                patterns.maxMessageLength());
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

}
