package io.github.dancan254.logguard.mask;

/**
 * A stand-in for a logged exception, carrying its masked message and its stack trace. The original
 * type is kept for {@link #toString()} so a printed trace still names the class that was thrown.
 */
public final class MaskedThrowable extends Throwable {

    private final String type;

    public MaskedThrowable(String type, String message, StackTraceElement[] stackTrace, Throwable cause) {
        super(message, cause, true, true);
        this.type = type;
        setStackTrace(stackTrace);
    }

    @Override
    public String toString() {
        String message = getLocalizedMessage();
        return message == null ? type : type + ": " + message;
    }
}
