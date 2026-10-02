package dev.portfolio.finance.service;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.portfolio.finance.entity.BuiltInCategory;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.CategoryRepository;

@Service
public class CategoryInitializationService {

    private final CategoryRepository categoryRepository;

    public CategoryInitializationService(
            CategoryRepository categoryRepository
    ) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void createDefaultCategories(User user) {

        List<Category> categories = Arrays.stream(BuiltInCategory.values())
                .map(definition -> Category.builtIn(user, definition))
                .toList();

        categoryRepository.saveAll(categories);
    }
}