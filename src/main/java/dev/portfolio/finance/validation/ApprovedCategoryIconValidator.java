package dev.portfolio.finance.validation;

import dev.portfolio.finance.entity.CategoryIcon;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ApprovedCategoryIconValidator implements ConstraintValidator<ApprovedCategoryIcon, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || CategoryIcon.isApproved(value);
    }
}
