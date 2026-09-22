package io.github.devoracode.upsert.exception;

/**
 * mybatis-plus-upsert 库抛出的运行时异常。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertException extends RuntimeException {

    public UpsertException(String message) {
        super(message);
    }

    public UpsertException(String message, Throwable cause) {
        super(message, cause);
    }
}
