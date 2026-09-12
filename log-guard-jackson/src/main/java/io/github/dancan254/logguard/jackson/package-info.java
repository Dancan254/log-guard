/**
 * A Jackson module for JSON-encoded logs.
 *
 * <p>Register {@code LogGuardModule} on an {@code ObjectMapper} you build for logging. It is
 * deliberately not auto-registered: adding it to an application's primary mapper would silently
 * mask REST responses as well as log output, which is a far more damaging failure than an
 * unmasked log line.
 */
package io.github.dancan254.logguard.jackson;
