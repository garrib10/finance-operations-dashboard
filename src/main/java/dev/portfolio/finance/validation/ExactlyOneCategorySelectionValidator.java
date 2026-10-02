package dev.portfolio.finance.validation;

import dev.portfolio.finance.dto.category.CategorySelection;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ExactlyOneCategorySelectionValidator
        implements ConstraintValidator<ExactlyOneCategorySelection, CategorySelection> {

    public static final String MISSING = "Choose an existing category or create a new one";
    public static final String BOTH = "Choose an existing category or a new category, not both";

    @Override
    public boolean isValid(CategorySelection selection, ConstraintValidatorContext context) {
        if (selection == null) {
            return true;
        }
        boolean existing = selection.categoryId() != null;
        boolean created = selection.newCategory() != null;
        if (existing != created) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(existing ? BOTH : MISSING)
                .addPropertyNode(existing ? "newCategory" : "categoryId")
                .addConstraintViolation();
        return false;
    }
}
