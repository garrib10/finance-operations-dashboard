package dev.portfolio.finance.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Class-level rule for {@code CategorySelection} requests: exactly one of
 * {@code categoryId} or {@code newCategory}. Violations are reported on those fields so
 * they appear in the standard field-validation response.
 */
@Documented
@Constraint(validatedBy = ExactlyOneCategorySelectionValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExactlyOneCategorySelection {

    String message() default "Choose an existing category or create a new one";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
