package dev.portfolio.finance.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import dev.portfolio.finance.dto.transaction.CreateTransactionRequest;
import dev.portfolio.finance.dto.transaction.PagedTransactionResponse;
import dev.portfolio.finance.dto.transaction.TransactionFilterRequest;
import dev.portfolio.finance.dto.transaction.TransactionResponse;
import dev.portfolio.finance.dto.transaction.UpdateTransactionRequest;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.service.TransactionService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(
            TransactionService transactionService
    ) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            Authentication authentication,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        TransactionResponse response =
                transactionService.createTransaction(
                        authentication.getName(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<PagedTransactionResponse> getTransactions(
            Authentication authentication,

            @RequestParam(required = false)
            TransactionType type,

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false)
            BigDecimal minAmount,

            @RequestParam(required = false)
            BigDecimal maxAmount,

            @RequestParam(required = false)
            String sortBy,

            @RequestParam(required = false)
            String sortDirection,

            @RequestParam(required = false)
            Integer page,

            @RequestParam(required = false)
            Integer size
    ) {
        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        type,
                        search,
                        startDate,
                        endDate,
                        minAmount,
                        maxAmount,
                        sortBy,
                        sortDirection,
                        page,
                        size
                );

        PagedTransactionResponse response =
                transactionService.searchTransactions(
                        authentication.getName(),
                        filters
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(
            Authentication authentication,
            @PathVariable Long id
    ) {
        TransactionResponse response =
                transactionService.getTransactionById(
                        authentication.getName(),
                        id
                );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTransactionRequest request
    ) {
        TransactionResponse response =
                transactionService.updateTransaction(
                        authentication.getName(),
                        id,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            Authentication authentication,
            @PathVariable Long id
    ) {
        transactionService.deleteTransaction(
                authentication.getName(),
                id
        );

        return ResponseEntity.noContent().build();
    }
}