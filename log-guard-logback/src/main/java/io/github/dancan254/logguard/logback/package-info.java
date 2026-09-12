/**
 * The Logback adapter: masking applied to the logging event itself.
 *
 * <p>{@code MaskingAppenderWrapper} wraps each appender and hands it a
 * {@code MaskingLoggingEvent}, which masks lazily. The wrapper sits above the async boundary —
 * {@code Logger} to wrapper to {@code AsyncAppender} to the appenders — because Logback will not
 * let anything replace an {@code AsyncAppender}'s single child. Because the event masks lazily
 * rather than in {@code prepareForDeferredProcessing()}, the reflection and the regex still run on
 * the async worker rather than the request thread.
 *
 * <p>Masking the event rather than the pattern layout is the central design decision. The
 * OpenTelemetry Logback appender reads {@code getFormattedMessage()} directly, so a layout-level
 * filter would redact the console and still export raw values over OTLP.
 *
 * <p>{@code MaskingThrowableProxy} extends Logback's own {@code ThrowableProxy} rather than merely
 * implementing the interface, because that same appender exports an exception only when it can pull
 * a real {@code Throwable} out of the event.
 */
package io.github.dancan254.logguard.logback;
