package io.github.devoracode.upsert.exception;

/**
 * Upsert 元数据解析失败时抛出的异常。
 * 包括实体类缺少 {@link io.github.devoracode.upsert.annotation.ConflictKey} 注解、
 * 没有可更新列或 MyBatis-Plus TableInfo 不可用等情况。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertMetaException extends UpsertException {

    /**
     * 使用指定的详情消息构造一个 UpsertMetaException。
     *
     * @param message 详情消息
     */
    public UpsertMetaException(String message) {
        super(message);
    }
}
