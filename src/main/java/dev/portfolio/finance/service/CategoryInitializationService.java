package dev.portfolio.finance.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

        List<Category> categories = List.of(

                new Category(user, "Housing", true),
                new Category(user, "Groceries", true),
                new Category(user, "Dining", true),
                new Category(user, "Transportation", true),
                new Category(user, "Utilities", true),
                new Category(user, "Insurance", true),
                new Category(user, "Healthcare", true),
                new Category(user, "Entertainment", true),
                new Category(user, "Shopping", true),
                new Category(user, "Travel", true),
                new Category(user, "Income", false),
                new Category(user, "Savings", false),
                new Category(user, "Other", true)
        );

        categoryRepository.saveAll(categories);
    }
}