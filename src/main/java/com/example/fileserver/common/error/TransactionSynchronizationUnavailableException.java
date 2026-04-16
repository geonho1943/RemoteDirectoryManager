package com.example.fileserver.common.error;

public class TransactionSynchronizationUnavailableException extends RuntimeException {

    public TransactionSynchronizationUnavailableException(String message) {
        super(message);
    }

    public TransactionSynchronizationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
