package dev.portfolio.finance.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.portfolio.finance.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Name order, with the ID as a tie-break so collation-equal names still sort stably. */
    List<Category> findAllByUserIdOrderByNameAscIdAsc(Long userId);

    Optional<Category> findByIdAndUserId(
            Long id,
            Long userId
    );

    boolean existsByIdAndUserId(
            Long id,
            Long userId
    );

    /** Friendly early check; {@code uk_categories_user_normalized_name} is authoritative. */
    boolean existsByUserIdAndNormalizedName(
            Long userId,
            String normalizedName
    );

    boolean existsByUserIdAndNormalizedNameAndIdNot(
            Long userId,
            String normalizedName,
            Long id
    );
}
