package dev.portfolio.finance.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@link CategoryIcon} as its semantic key; unknown stored keys fail loudly. */
@Converter
public class CategoryIconConverter implements AttributeConverter<CategoryIcon, String> {

    @Override
    public String convertToDatabaseColumn(CategoryIcon icon) {
        return icon == null ? null : icon.key();
    }

    @Override
    public CategoryIcon convertToEntityAttribute(String key) {
        if (key == null) {
            return null;
        }
        return CategoryIcon.fromKey(key)
                .orElseThrow(() -> new IllegalStateException("Unsupported category icon key"));
    }
}
