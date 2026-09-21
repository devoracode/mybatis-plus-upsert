package io.github.devoracode.upsert.exception;

/**
 * 启动注入期解析实体元数据失败时抛出的异常：没有 {@link io.github.devoracode.upsert.annotation.ConflictKey}
 * 字段、没有可更新列，或冲突键声明了 {@code insertStrategy = NEVER}。
 * 实体完全没有 {@code @ConflictKey} 时注入器会跳过该 Mapper，不会走到解析这一步。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertMetaException extends UpsertException {

    public UpsertMetaException(String message) {
        super(message);
    }
}
