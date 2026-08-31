package io.github.dancan254.logguard.log4j2;

import io.github.dancan254.logguard.LogGuardMasker;

/**
 * Log4j2 builds plugins itself, from configuration, with no way to pass one a collaborator. The
 * starter puts the masker here before the configuration is read.
 */
public final class LogGuardMaskerHolder {

    private static volatile LogGuardMasker masker;

    private LogGuardMaskerHolder() {
    }

    public static void set(LogGuardMasker replacement) {
        masker = replacement;
    }

    static LogGuardMasker get() {
        return masker;
    }
}
