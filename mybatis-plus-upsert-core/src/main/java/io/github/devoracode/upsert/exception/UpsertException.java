package io.github.devoracode.upsert.exception;

/**
 * Runtime exception thrown by the mybatis-plus-upsert library.
 * Covers configuration errors, dialect creation failures, and SQL generation issues.
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertException extends RuntimeException {

    /**
     * Constructs an UpsertException with the specified detail message.
     *
     * @param message the detail message
     */
    public UpsertException(String message) {
        super(message);
    }

    /**
     * Constructs an UpsertException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public UpsertException(String message, Throwable cause) {
        super(message, cause);
    }
}