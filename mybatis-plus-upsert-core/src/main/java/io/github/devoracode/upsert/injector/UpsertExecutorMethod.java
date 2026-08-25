package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.dialect.UpsertDialect;

/**
 * 返回 {@code BatchResult} 列表的批量 Upsert SQL 注入方法。
 * 由 UpsertMapper 中的 {@code upsert(Collection)} 和 {@code upsert(Collection, int)} 方法使用。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertExecutorMethod extends AbstractUpsertMethod {

    /**
     * 在 Mapper 中注册的方法名。
     */
    public static final String METHOD_NAME = UpsertMethodNames.UPSERT_EXECUTOR;

    /**
     * 使用给定的方言和默认填充策略创建新的 UpsertExecutorMethod。
     *
     * @param dialect 用于 SQL 生成的 Upsert 方言
     */
    public UpsertExecutorMethod(UpsertDialect dialect) {
        super(METHOD_NAME, dialect, false);
    }

    /**
     * 使用给定的方言和填充策略创建新的 UpsertExecutorMethod。
     *
     * @param dialect      用于 SQL 生成的 Upsert 方言
     * @param fillStrategy SQL 绑定前应用的自动填充策略
     * @since 1.6.0
     */
    public UpsertExecutorMethod(UpsertDialect dialect, FillStrategy fillStrategy) {
        super(METHOD_NAME, dialect, false, fillStrategy);
    }
}
