package dev.portfolio.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.User;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class CategoryRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void shouldFindAllCategoriesForUserOrderedByNameAscending() {

        User user = userRepository.save(
                new User(
                        "Test",
                        "User",
                        "test@example.com",
                        "hashed-password"
                )
        );

        categoryRepository.save(
                new Category(user, "Utilities", true)
        );

        categoryRepository.save(
                new Category(user, "Groceries", true)
        );

        categoryRepository.save(
                new Category(user, "Dining", true)
        );

        List<Category> categories =
                categoryRepository
                        .findAllByUserIdOrderByNameAsc(
                                user.getId()
                        );

        assertThat(categories)
                .extracting(Category::getName)
                .containsExactly(
                        "Dining",
                        "Groceries",
                        "Utilities"
                );
    }

    @Test
    void shouldOnlyReturnCategoriesOwnedByUser() {

        User firstUser = userRepository.save(
                new User(
                        "First",
                        "User",
                        "first@example.com",
                        "hashed-password"
                )
        );

        User secondUser = userRepository.save(
                new User(
                        "Second",
                        "User",
                        "second@example.com",
                        "hashed-password"
                )
        );

        categoryRepository.save(
                new Category(
                        firstUser,
                        "Groceries",
                        true
                )
        );

        categoryRepository.save(
                new Category(
                        secondUser,
                        "Travel",
                        true
                )
        );

        List<Category> categories =
                categoryRepository
                        .findAllByUserIdOrderByNameAsc(
                                firstUser.getId()
                        );

        assertThat(categories)
                .hasSize(1);

        assertThat(categories.getFirst().getName())
                .isEqualTo("Groceries");

        assertThat(categories.getFirst().getUser().getId())
                .isEqualTo(firstUser.getId());
    }

    @Test
    void shouldFindCategoryByIdAndUserId() {

        User user = userRepository.save(
                new User(
                        "Test",
                        "User",
                        "test@example.com",
                        "hashed-password"
                )
        );

        Category category =
                categoryRepository.save(
                        new Category(
                                user,
                                "Groceries",
                                true
                        )
                );

        var result =
                categoryRepository.findByIdAndUserId(
                        category.getId(),
                        user.getId()
                );

        assertThat(result)
                .isPresent();

        assertThat(result.get().getName())
                .isEqualTo("Groceries");
    }

    @Test
    void shouldNotFindCategoryOwnedByDifferentUser() {

        User owner = userRepository.save(
                new User(
                        "Owner",
                        "User",
                        "owner@example.com",
                        "hashed-password"
                )
        );

        User otherUser = userRepository.save(
                new User(
                        "Other",
                        "User",
                        "other@example.com",
                        "hashed-password"
                )
        );

        Category category =
                categoryRepository.save(
                        new Category(
                                owner,
                                "Groceries",
                                true
                        )
                );

        var result =
                categoryRepository.findByIdAndUserId(
                        category.getId(),
                        otherUser.getId()
                );

        assertThat(result)
                .isEmpty();
    }

    @Test
    void shouldDetectExistingCategoryNameIgnoringCase() {

        User user = userRepository.save(
                new User(
                        "Test",
                        "User",
                        "test@example.com",
                        "hashed-password"
                )
        );

        categoryRepository.save(
                new Category(
                        user,
                        "Groceries",
                        true
                )
        );

        boolean exists =
                categoryRepository
                        .existsByUserIdAndNameIgnoreCase(
                                user.getId(),
                                "gRoCeRiEs"
                        );

        assertThat(exists)
                .isTrue();
    }

    @Test
    void shouldNotTreatAnotherUsersCategoryAsDuplicate() {

        User firstUser = userRepository.save(
                new User(
                        "First",
                        "User",
                        "first@example.com",
                        "hashed-password"
                )
        );

        User secondUser = userRepository.save(
                new User(
                        "Second",
                        "User",
                        "second@example.com",
                        "hashed-password"
                )
        );

        categoryRepository.save(
                new Category(
                        firstUser,
                        "Groceries",
                        true
                )
        );

        boolean exists =
                categoryRepository
                        .existsByUserIdAndNameIgnoreCase(
                                secondUser.getId(),
                                "Groceries"
                        );

        assertThat(exists)
                .isFalse();
    }
}