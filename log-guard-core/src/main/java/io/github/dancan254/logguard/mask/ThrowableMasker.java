package io.github.dancan254.logguard.mask;

import io.github.dancan254.logguard.LogGuardMasker;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Rebuilds a throwable chain with masked messages. Shared by every adapter: the shape of the
 * logging framework's rewrite point differs, the leak in {@code Key (email)=(…) already exists}
 * does not.
 */
public final class ThrowableMasker {

    /** A cause chain may be cyclic and may be arbitrarily long; either one hangs a log call. */
    public static final int MAX_DEPTH = 10;

    private ThrowableMasker() {
    }

    public static Throwable mask(Throwable thrown, LogGuardMasker masker) {
        return mask(thrown, masker::maskMessage);
    }

    /** Returns the original throwable when nothing in the chain changed. */
    public static Throwable mask(Throwable thrown, UnaryOperator<String> maskMessage) {
        if (thrown == null) {
            return null;
        }
        return rebuild(thrown, maskMessage, MAX_DEPTH, new IdentityHashMap<>());
    }

    private static Throwable rebuild(Throwable thrown, UnaryOperator<String> maskMessage,
                                     int remainingDepth, Map<Throwable, Boolean> seen) {
        if (thrown == null || remainingDepth <= 0 || seen.put(thrown, Boolean.TRUE) != null) {
            return null;
        }
        String message = thrown.getMessage();
        String masked = message == null ? null : maskMessage.apply(message);
        Throwable cause = rebuild(thrown.getCause(), maskMessage, remainingDepth - 1, seen);
        boolean causeUnchanged = cause == thrown.getCause();

        Throwable[] suppressed = thrown.getSuppressed();
        Throwable[] maskedSuppressed = new Throwable[suppressed.length];
        boolean suppressedUnchanged = true;
        for (int index = 0; index < suppressed.length; index++) {
            maskedSuppressed[index] = rebuild(suppressed[index], maskMessage, remainingDepth - 1, seen);
            suppressedUnchanged &= maskedSuppressed[index] == suppressed[index];
        }

        if (java.util.Objects.equals(message, masked) && causeUnchanged && suppressedUnchanged) {
            return thrown;
        }
        MaskedThrowable rebuilt = new MaskedThrowable(thrown.getClass().getName(), masked,
                thrown.getStackTrace(), cause);
        for (Throwable each : maskedSuppressed) {
            if (each != null) {
                rebuilt.addSuppressed(each);
            }
        }
        return rebuilt;
    }
}
