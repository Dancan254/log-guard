package io.github.dancan254.logguard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field or record component as personal data, so log-guard masks it instead of letting
 * {@code toString()} print it.
 *
 * <p>A bare {@code @Pii} redacts the value entirely. Pick a different {@link MaskStrategy} when the
 * masked form still has to be useful — {@code PARTIAL} to keep a record recognisable to support
 * staff, {@code HASH} to correlate one person across log lines without storing the value.
 *
 * <pre>{@code
 * public record Customer(
 *         Long id,
 *         @Pii(strategy = MaskStrategy.HASH)    String email,
 *         @Pii(strategy = MaskStrategy.PARTIAL) String phoneNumber,
 *         @Pii                                  LocalDate dateOfBirth,
 *         String city) {}
 * }</pre>
 *
 * <p>A class is rendered field by field only when it carries at least one of these; anything else
 * is left to its own {@code toString()}. The annotation is retained at runtime because masking
 * happens as the event is written, not at compile time — which is also why Lombok's
 * {@code @ToString.Exclude} cannot hide a field from log-guard. Use
 * {@code @Pii(strategy = DROP)} for that.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Pii {

    /**
     * How the value is masked. Defaults to {@link MaskStrategy#REDACT}.
     *
     * @return the strategy applied to this field
     */
    MaskStrategy strategy() default MaskStrategy.REDACT;

    /**
     * What kind of personal data this holds. Never changes the output — it exists so an audit can
     * answer which fields are financial and which are credentials. Defaults to
     * {@link PiiCategory#PERSONAL}.
     *
     * @return the category recorded for this field
     */
    PiiCategory category() default PiiCategory.PERSONAL;
}
