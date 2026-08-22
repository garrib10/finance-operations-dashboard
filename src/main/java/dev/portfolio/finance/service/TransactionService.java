package dev.portfolio.finance.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.portfolio.finance.dto.transaction.CreateTransactionRequest;
import dev.portfolio.finance.dto.transaction.TransactionResponse;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.exception.transaction.TransactionNotFoundException;

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
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found"
                ));

        return mapToResponse(transaction);
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