package io.github.devoracode.upsert.exception;

/**
 * Exception thrown when upsert metadata parsing fails.
 * This includes cases where the entity class lacks a {@link io.github.devoracode.upsert.annotation.ConflictKey}
 * annotation, has no updatable columns, or the MyBatis-Plus TableInfo is not available.
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertMetaException extends UpsertException {

    /**
     * Constructs an UpsertMetaException with the specified detail message.
     *
     * @param message the detail message
     */
    public UpsertMetaException(String message) {
        super(message);
    }
}