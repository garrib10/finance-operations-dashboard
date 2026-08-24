package dev.portfolio.finance.exception.transaction;

public class InvalidTransactionFilterException extends RuntimeException {

    public InvalidTransactionFilterException(String message) {
        super(message);
    }
}