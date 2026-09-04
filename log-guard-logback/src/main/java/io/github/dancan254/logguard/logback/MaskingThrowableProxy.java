package io.github.dancan254.logguard.logback;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.github.dancan254.logguard.LogGuardMasker;
import io.github.dancan254.logguard.mask.MaskedThrowable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Exception messages are the channel that leaks without anyone writing a log statement:
 * {@code Key (email)=(jane@acme.io) already exists} comes straight from the driver.
 */
public final class MaskingThrowableProxy extends ThrowableProxy {

    static final int MAX_DEPTH = 10;

    private static final IThrowableProxy[] NO_SUPPRESSED = new IThrowableProxy[0];

    /**
     * The OpenTelemetry Logback appender exports an exception only when the proxy is a
     * ThrowableProxy it can pull a Throwable out of, so this extends one and hands over a masked
     * stand-in. The superclass is fed a throwable with no trace and no cause; every method that
     * reads it is overridden.
     */
    private static final Throwable UNUSED = unusedThrowable();

    private Throwable maskedThrowable;

    private final IThrowableProxy delegate;
    private final LogGuardMasker masker;
    private final int remainingDepth;
    private final Map<IThrowableProxy, Boolean> seen;

    private String maskedMessage;
    private boolean masked;
    private IThrowableProxy cause;
    private boolean causeResolved;
    private IThrowableProxy[] suppressed;

    public MaskingThrowableProxy(IThrowableProxy delegate, LogGuardMasker masker) {
        this(delegate, masker, MAX_DEPTH, new IdentityHashMap<>());
    }

    private MaskingThrowableProxy(IThrowableProxy delegate, LogGuardMasker masker,
                                  int remainingDepth, Map<IThrowableProxy, Boolean> seen) {
        super(UNUSED, new HashSet<>());
        this.delegate = delegate;
        this.masker = masker;
        this.remainingDepth = remainingDepth;
        this.seen = seen;
        seen.put(delegate, Boolean.TRUE);
    }

    @Override
    public String getMessage() {
        if (masked) {
            return maskedMessage;
        }
        masked = true;
        try {
            maskedMessage = masker.maskMessage(delegate.getMessage());
        } catch (RuntimeException | LinkageError cause) {
            maskedMessage = MaskingLoggingEvent.MASKING_FAILED_MESSAGE;
        }
        return maskedMessage;
    }

    @Override
    public IThrowableProxy getCause() {
        if (!causeResolved) {
            causeResolved = true;
            cause = wrap(delegate.getCause());
        }
        return cause;
    }

    @Override
    public IThrowableProxy[] getSuppressed() {
        if (suppressed != null) {
            return suppressed;
        }
        IThrowableProxy[] original = delegate.getSuppressed();
        if (original == null || original.length == 0) {
            suppressed = NO_SUPPRESSED;
            return suppressed;
        }
        // wrap() drops a proxy past the depth limit or one already seen. A hole left in this array
        // reaches everything that iterates it, and Throwable.addSuppressed rejects a null outright.
        IThrowableProxy[] wrapped = new IThrowableProxy[original.length];
        int kept = 0;
        for (IThrowableProxy each : original) {
            IThrowableProxy replacement = wrap(each);
            if (replacement != null) {
                wrapped[kept++] = replacement;
            }
        }
        suppressed = kept == wrapped.length ? wrapped : Arrays.copyOf(wrapped, kept);
        return suppressed;
    }

    /**
     * A cause chain may be cyclic and may be arbitrarily long; either one turns a log call into a
     * hang. Beyond the limit the proxy is dropped rather than passed through raw.
     */
    private IThrowableProxy wrap(IThrowableProxy target) {
        if (target == null || remainingDepth <= 1 || seen.containsKey(target)) {
            return null;
        }
        return new MaskingThrowableProxy(target, masker, remainingDepth - 1, seen);
    }

    /**
     * The stand-in the OTLP exporter sees: masked messages, original stack frames and type names.
     * An exception whose chain held nothing to mask is handed over untouched, so the exported
     * exception.type stays exact for everything that was never a leak in the first place.
     */
    @Override
    public Throwable getThrowable() {
        if (maskedThrowable == null) {
            maskedThrowable = delegate instanceof ThrowableProxy original && isUnchanged(this, delegate)
                    ? original.getThrowable()
                    : toThrowable(this);
        }
        return maskedThrowable;
    }

    private static boolean isUnchanged(IThrowableProxy masked, IThrowableProxy raw) {
        if (masked == null || raw == null) {
            return masked == raw;
        }
        if (!java.util.Objects.equals(masked.getMessage(), raw.getMessage())) {
            return false;
        }
        IThrowableProxy[] maskedSuppressed = masked.getSuppressed();
        IThrowableProxy[] rawSuppressed = raw.getSuppressed();
        int maskedCount = maskedSuppressed == null ? 0 : maskedSuppressed.length;
        int rawCount = rawSuppressed == null ? 0 : rawSuppressed.length;
        if (maskedCount != rawCount) {
            return false;
        }
        for (int index = 0; index < maskedCount; index++) {
            if (!isUnchanged(maskedSuppressed[index], rawSuppressed[index])) {
                return false;
            }
        }
        return isUnchanged(masked.getCause(), raw.getCause());
    }

    private static Throwable toThrowable(IThrowableProxy proxy) {
        if (proxy == null) {
            return null;
        }
        MaskedThrowable throwable = new MaskedThrowable(proxy.getClassName(), proxy.getMessage(),
                toStackTrace(proxy.getStackTraceElementProxyArray()), toThrowable(proxy.getCause()));
        IThrowableProxy[] suppressed = proxy.getSuppressed();
        if (suppressed != null) {
            for (IThrowableProxy each : suppressed) {
                throwable.addSuppressed(toThrowable(each));
            }
        }
        return throwable;
    }

    private static StackTraceElement[] toStackTrace(StackTraceElementProxy[] proxies) {
        if (proxies == null) {
            return new StackTraceElement[0];
        }
        StackTraceElement[] elements = new StackTraceElement[proxies.length];
        for (int index = 0; index < proxies.length; index++) {
            elements[index] = proxies[index].getStackTraceElement();
        }
        return elements;
    }

    private static Throwable unusedThrowable() {
        Throwable throwable = new Throwable("log-guard");
        throwable.setStackTrace(new StackTraceElement[0]);
        return throwable;
    }

    @Override
    public String getClassName() {
        return delegate.getClassName();
    }

    @Override
    public StackTraceElementProxy[] getStackTraceElementProxyArray() {
        return delegate.getStackTraceElementProxyArray();
    }

    @Override
    public int getCommonFrames() {
        return delegate.getCommonFrames();
    }

    @Override
    public boolean isCyclic() {
        return delegate.isCyclic();
    }
}
