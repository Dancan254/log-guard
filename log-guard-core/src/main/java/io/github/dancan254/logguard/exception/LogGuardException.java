package io.github.dancan254.logguard.exception;

public abstract class LogGuardException extends RuntimeException {

    protected LogGuardException(String message) {
        super(message);
    }
}
