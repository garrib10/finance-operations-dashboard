package dev.portfolio.finance.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TransactionPageSizeValidator implements ConstraintValidator<ValidTransactionPageSize, Integer> {
    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || value == 10 || value == 25 || value == 50;
    }
}
