package io.github.dancan254.logguard.exception;

public class InvalidPatternException extends LogGuardException {

    public InvalidPatternException(String name, String regex, String reason) {
        super("log-guard.patterns.custom entry '" + name + "' does not compile: " + regex
                + " — " + reason + ". The patterns are compiled into one alternation before the "
                + "logging system is ready, so this cannot be reported as an ordinary startup error.");
    }
}
