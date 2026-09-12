/**
 * The Log4j2 adapter, built on a {@code RewritePolicy} rather than appender wrapping.
 *
 * <p>The extension point differs from Logback's; the engine does not. Put every appender behind the
 * rewrite so none of them sees the raw event.
 *
 * <p>One behavioural difference is worth knowing. SLF4J's Log4j2 binding stores key-value pairs as
 * strings and calls {@code String.valueOf} inside {@code addKeyValue}, so no object survives to the
 * rewrite point and those pairs receive the pattern layer alone. Naming the key in the MDC
 * redaction list closes that gap. Log arguments are unaffected and stay type aware on both
 * backends.
 */
package io.github.dancan254.logguard.log4j2;
