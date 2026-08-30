package io.github.dancan254.logguard;

public enum MaskStrategy {

    REDACT,

    PARTIAL,

    /** Salted digest, stable across log lines and instances so a user stays correlatable. */
    HASH,

    DROP
}
