package dev.portfolio.finance.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import dev.portfolio.finance.entity.Transaction;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    List<Transaction> findAllByUserIdOrderByTransactionDateDesc(Long userId);

    Optional<Transaction> findByIdAndUserId(
            Long id,
            Long userId
    );
}