package io.github.dancan254.logguard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface Pii {

    MaskStrategy strategy() default MaskStrategy.REDACT;

    PiiCategory category() default PiiCategory.PERSONAL;
}
