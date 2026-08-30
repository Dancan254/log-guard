package io.github.dancan254.logguard.exception;

public class MissingHashSaltException extends LogGuardException {

    public MissingHashSaltException() {
        super("log-guard.hash-salt must be set when any field uses MaskStrategy.HASH. "
                + "A per-boot random salt would break correlation across instances, "
                + "and an unsalted digest is reversible with a rainbow table.");
    }
}
