package dev.portfolio.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.support.TestDataFactory;

@ExtendWith(MockitoExtension.class)
class CategoryInitializationServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Test
    void shouldCreateDefaultCategoriesForNewUser() {
        // Arrange
        User user = TestDataFactory.createUser();

        CategoryInitializationService service =
                new CategoryInitializationService(
                        categoryRepository
                );

        // Act
        service.createDefaultCategories(user);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Category>> categoryCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(categoryRepository)
                .saveAll(categoryCaptor.capture());

        List<Category> categories =
                categoryCaptor.getValue();

        assertEquals(
                13,
                categories.size()
        );

        assertTrue(
                categories.stream()
                        .allMatch(category ->
                                category.getUser() == user)
        );

        assertTrue(
                categories.stream()
                        .anyMatch(category ->
                                category.getName().equals("Housing")
                                        && category.isBudgetEnabled())
        );

        assertTrue(
                categories.stream()
                        .anyMatch(category ->
                                category.getName().equals("Groceries")
                                        && category.isBudgetEnabled())
        );

        assertTrue(
                categories.stream()
                        .anyMatch(category ->
                                category.getName().equals("Other")
                                        && category.isBudgetEnabled())
        );

        assertTrue(
                categories.stream()
                        .anyMatch(category ->
                                category.getName().equals("Income")
                                        && !category.isBudgetEnabled())
        );

        assertTrue(
                categories.stream()
                        .anyMatch(category ->
                                category.getName().equals("Savings")
                                        && !category.isBudgetEnabled())
        );

        assertFalse(
                categories.stream()
                        .anyMatch(category ->
                                category.getName().isBlank())
        );
    }
}