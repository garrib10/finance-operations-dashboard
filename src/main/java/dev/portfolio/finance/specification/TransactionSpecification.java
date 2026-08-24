package dev.portfolio.finance.specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.TransactionType;

public final class TransactionSpecification {

    private TransactionSpecification() {
    }

    public static Specification<Transaction> belongsToUser(Long userId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("user").get("id"),
                        userId
                );
    }

    public static Specification<Transaction> hasType(
            TransactionType type
    ) {
        if (type == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("type"),
                        type
                );
    }

    public static Specification<Transaction> descriptionContains(
            String search
    ) {
        if (search == null || search.isBlank()) {
            return Specification.unrestricted();
        }

        String normalizedSearch =
                "%" + search.trim().toLowerCase() + "%";

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(
                        criteriaBuilder.lower(
                                root.get("description")
                        ),
                        normalizedSearch
                );
    }

    public static Specification<Transaction> dateOnOrAfter(
            LocalDate startDate
    ) {
        if (startDate == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(
                        root.get("transactionDate"),
                        startDate
                );
    }

    public static Specification<Transaction> dateOnOrBefore(
            LocalDate endDate
    ) {
        if (endDate == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(
                        root.get("transactionDate"),
                        endDate
                );
    }

    public static Specification<Transaction> amountAtLeast(
            BigDecimal minAmount
    ) {
        if (minAmount == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(
                        root.get("amount"),
                        minAmount
                );
    }

    public static Specification<Transaction> amountAtMost(
            BigDecimal maxAmount
    ) {
        if (maxAmount == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(
                        root.get("amount"),
                        maxAmount
                );
    }
}