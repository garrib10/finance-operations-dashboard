package dev.portfolio.finance.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Null or blank (meaning "not supplied"), or an exact key from {@code CategoryIcon}. */
@Documented
@Constraint(validatedBy = ApprovedCategoryIconValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApprovedCategoryIcon {

    String message() default "Icon must be one of the approved category icons";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
