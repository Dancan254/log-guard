/**
 * Rendering an annotated object, and anything nested inside it, as a masked string.
 *
 * <p>{@code ObjectRenderer} walks objects, arrays, collections and maps recursively, bounded by a
 * depth limit, an element cap and an identity cycle guard so a bidirectional JPA relationship
 * terminates instead of traversing the graph.
 *
 * <p>Reflection only enters classes that carry {@code Pii}. A class with no annotation of its own
 * is still rendered when one of its declared field types carries one, because a field typed
 * {@code Object} hides whatever it actually holds.
 */
package io.github.dancan254.logguard.render;
