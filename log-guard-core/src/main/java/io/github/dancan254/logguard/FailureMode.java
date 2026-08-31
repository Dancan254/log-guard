package io.github.dancan254.logguard;

/** What an appender does with an event log-guard could not mask. */
public enum FailureMode {

    /** Keep the event, replace its payload with a notice. The default: never leaks, never silent. */
    PLACEHOLDER,

    /** Discard the event. Choose this when a leak matters more than the missing line. */
    DROP,

    /** Emit the event unmasked. Choose this only when the log is already inside the trust boundary. */
    PASSTHROUGH
}
