/**
 * The public surface of log-guard: the {@code Pii} annotation, the masking strategies, and the
 * engine that applies them.
 *
 * <p>Annotate a field or record component with {@code Pii} and pick a {@code MaskStrategy}:
 * {@code REDACT} (the default), {@code PARTIAL}, {@code HASH} or {@code DROP}. {@code PiiCategory}
 * records what kind of data a field holds and never changes the output; it exists so an audit can
 * answer which fields are financial and which are credentials.
 *
 * <p>{@code LogGuardMasker} is the entry point a logging adapter drives. Everything here is
 * framework-free and dependency-free, which is the constraint that makes the library approvable:
 * a privacy library with a transitive dependency tree is a harder sell to the team that signs it
 * off.
 */
package io.github.dancan254.logguard;
