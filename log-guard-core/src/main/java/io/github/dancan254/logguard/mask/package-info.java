/**
 * Turning a value into its masked form.
 *
 * <p>{@code ValueMasker} applies one strategy to one value. {@code PARTIAL} is length aware,
 * because a mask that shrinks with its input leaks the input, and {@code HASH} is a salted SHA-256
 * truncated to six hex characters, so one person stays correlatable across lines without the value
 * itself ever being written.
 *
 * <p>{@code ThrowableMasker} walks a throwable chain, including causes and suppressed exceptions,
 * and produces a {@code MaskedThrowable} only where masking actually changed something. A chain
 * that held nothing to mask is passed through untouched, which keeps the reported exception type
 * exact.
 */
package io.github.dancan254.logguard.mask;
