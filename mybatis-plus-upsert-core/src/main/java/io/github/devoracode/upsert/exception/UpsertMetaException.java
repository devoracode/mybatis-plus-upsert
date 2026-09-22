package io.github.devoracode.upsert.exception;

/**
 * 启动注入期解析实体元数据失败时抛出的异常。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertMetaException extends UpsertException {

    public UpsertMetaException(String message) {
        super(message);
    }
}
