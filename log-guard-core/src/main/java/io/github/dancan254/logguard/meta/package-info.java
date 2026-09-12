/**
 * What log-guard knows about a class, and the cache that keeps knowing it cheap.
 *
 * <p>{@code PiiMetadataCache} reads the annotations on a class once and remembers the result
 * against the class itself, so the common case — an argument whose class carries no {@code Pii} at
 * all — costs a lookup and nothing more. Reflection on every log statement would make the library
 * unusable on a hot path, so it happens once per class rather than once per event.
 */
package io.github.dancan254.logguard.meta;
