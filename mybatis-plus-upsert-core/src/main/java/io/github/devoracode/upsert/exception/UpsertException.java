package io.github.devoracode.upsert.exception;

public class UpsertException extends RuntimeException {

    public UpsertException(String message) {
        super(message);
    }

    public UpsertException(String message, Throwable cause) {
        super(message, cause);
    }
}
