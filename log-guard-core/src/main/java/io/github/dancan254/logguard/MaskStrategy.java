package io.github.dancan254.logguard;

/**
 * How a value marked with {@link Pii} is rendered once masked.
 *
 * <p>The choice is a trade between hiding the value and keeping the log useful. Redaction hides
 * everything; the other three each give something back, and give up a little in exchange.
 */
public enum MaskStrategy {

    /** Replaces the value with {@code ***}. The default, and the right choice when the value has
     * no analytical use once hidden. */
    REDACT,

    /**
     * Keeps enough of the value to recognise the record without revealing it, for example
     * {@code j****@example.com} or {@code +2547****244}.
     *
     * <p>Length aware on purpose: a mask that shrank with its input would leak the input's length,
     * so anything too short to hide inside falls back to {@code ***}.
     */
    PARTIAL,

    /** Salted digest, stable across log lines and instances so a user stays correlatable.
     * Requires a configured salt; rotating the salt breaks correlation with older logs, which is
     * the intended trade. */
    HASH,

    /** Leaves the field out of the rendered output entirely. Use it for values that should never
     * appear, and as the opt-out Lombok's source-retained {@code @ToString.Exclude} cannot provide. */
    DROP
}
