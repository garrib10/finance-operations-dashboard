package dev.portfolio.finance.service;

import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.portfolio.finance.dto.transaction.CreateTransactionRequest;
import dev.portfolio.finance.dto.transaction.PagedTransactionResponse;
import dev.portfolio.finance.dto.transaction.TransactionFilterRequest;
import dev.portfolio.finance.dto.transaction.TransactionResponse;
import dev.portfolio.finance.dto.transaction.UpdateTransactionRequest;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.transaction.InvalidTransactionFilterException;
import dev.portfolio.finance.exception.transaction.TransactionNotFoundException;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.specification.TransactionSpecification;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            UserRepository userRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TransactionResponse createTransaction(
            String authenticatedEmail,
            CreateTransactionRequest request
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Transaction transaction = new Transaction(
                user,
                request.type(),
                request.amount(),
                request.description().trim(),
                request.transactionDate()
        );

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        return mapToResponse(savedTransaction);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions(
            String authenticatedEmail
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        return transactionRepository
                .findAllByUserIdOrderByTransactionDateDesc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(
            String authenticatedEmail,
            Long transactionId
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Transaction transaction = transactionRepository
                .findByIdAndUserId(
                        transactionId,
                        user.getId()
                )
                .orElseThrow(() ->
                        new TransactionNotFoundException(
                                "Transaction not found"
                        )
                );

        return mapToResponse(transaction);
    }

    @Transactional
    public TransactionResponse updateTransaction(
            String authenticatedEmail,
            Long transactionId,
            UpdateTransactionRequest request
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Transaction transaction = transactionRepository
                .findByIdAndUserId(
                        transactionId,
                        user.getId()
                )
                .orElseThrow(() ->
                        new TransactionNotFoundException(
                                "Transaction not found"
                        )
                );

        transaction.update(
                request.type(),
                request.amount(),
                request.description().trim(),
                request.transactionDate()
        );

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        return mapToResponse(savedTransaction);
    }

    @Transactional
    public void deleteTransaction(
            String authenticatedEmail,
            Long transactionId
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Transaction transaction = transactionRepository
                .findByIdAndUserId(
                        transactionId,
                        user.getId()
                )
                .orElseThrow(() ->
                        new TransactionNotFoundException(
                                "Transaction not found"
                        )
                );

        transactionRepository.delete(transaction);
    }

    @Transactional(readOnly = true)
    public PagedTransactionResponse searchTransactions(
            String authenticatedEmail,
            TransactionFilterRequest filters
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        validateFilters(filters);

        int page = filters.page() != null
                ? filters.page()
                : 0;

        int size = filters.size() != null
                ? filters.size()
                : 20;

        String sortBy = filters.sortBy() != null
                ? filters.sortBy()
                : "transactionDate";

        Sort.Direction direction =
                "asc".equalsIgnoreCase(filters.sortDirection())
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, sortBy)
        );

        Specification<Transaction> specification =
                Specification.allOf(
                        TransactionSpecification.belongsToUser(
                                user.getId()
                        ),
                        TransactionSpecification.hasType(
                                filters.type()
                        ),
                        TransactionSpecification.descriptionContains(
                                filters.search()
                        ),
                        TransactionSpecification.dateOnOrAfter(
                                filters.startDate()
                        ),
                        TransactionSpecification.dateOnOrBefore(
                                filters.endDate()
                        ),
                        TransactionSpecification.amountAtLeast(
                                filters.minAmount()
                        ),
                        TransactionSpecification.amountAtMost(
                                filters.maxAmount()
                        )
                );

        Page<TransactionResponse> result =
                transactionRepository
                        .findAll(specification, pageable)
                        .map(this::mapToResponse);

        return new PagedTransactionResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    private void validateFilters(
            TransactionFilterRequest filters
    ) {
        Set<String> allowedSortFields = Set.of(
                "transactionDate",
                "amount",
                "createdAt"
        );

        if (filters.startDate() != null
                && filters.endDate() != null
                && filters.startDate().isAfter(filters.endDate())) {

            throw new InvalidTransactionFilterException(
                    "Start date cannot be after end date"
            );
        }

        if (filters.minAmount() != null
                && filters.minAmount().signum() < 0) {

            throw new InvalidTransactionFilterException(
                    "Minimum amount cannot be negative"
            );
        }

        if (filters.maxAmount() != null
                && filters.maxAmount().signum() < 0) {

            throw new InvalidTransactionFilterException(
                    "Maximum amount cannot be negative"
            );
        }

        if (filters.minAmount() != null
                && filters.maxAmount() != null
                && filters.minAmount().compareTo(
                        filters.maxAmount()
                ) > 0) {

            throw new InvalidTransactionFilterException(
                    "Minimum amount cannot be greater than maximum amount"
            );
        }

        if (filters.sortBy() != null
                && !allowedSortFields.contains(filters.sortBy())) {

            throw new InvalidTransactionFilterException(
                    "Invalid sort field"
            );
        }

        if (filters.sortDirection() != null
                && !filters.sortDirection().equalsIgnoreCase("asc")
                && !filters.sortDirection().equalsIgnoreCase("desc")) {

            throw new InvalidTransactionFilterException(
                    "Sort direction must be asc or desc"
            );
        }

        if (filters.page() != null
                && filters.page() < 0) {

            throw new InvalidTransactionFilterException(
                    "Page cannot be negative"
            );
        }

        if (filters.size() != null
                && (filters.size() < 1
                || filters.size() > 100)) {

            throw new InvalidTransactionFilterException(
                    "Size must be between 1 and 100"
            );
        }
    }

    private TransactionResponse mapToResponse(
            Transaction transaction
    ) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}