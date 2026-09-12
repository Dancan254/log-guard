/**
 * The exception hierarchy, rooted at {@code LogGuardException}.
 *
 * <p>Every one of these is thrown at startup rather than while logging. That is deliberate: a
 * misconfiguration should stop the application before it has written its first line, not surface as
 * a leak on a line nobody is watching. {@code MissingHashSaltException} covers a {@code HASH}
 * strategy with a blank salt, {@code InvalidPatternException} a custom pattern that does not
 * compile, and {@code UnannotatedEntityException} the startup validator running in its failing
 * mode.
 */
package io.github.dancan254.logguard.exception;
