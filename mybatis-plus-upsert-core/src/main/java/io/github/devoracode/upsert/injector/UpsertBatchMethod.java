package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.dialect.UpsertDialect;

/**
 * 批量 Upsert SQL 注入方法。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertBatchMethod extends AbstractUpsertMethod {

    /**
     * 在 Mapper 中注册的方法名。
     */
    public static final String METHOD_NAME = UpsertMethodNames.UPSERT_BATCH;

    /**
     * 使用给定的方言和默认填充策略创建新的 UpsertBatchMethod。
     *
     * @param dialect 用于 SQL 生成的 Upsert 方言
     */
    public UpsertBatchMethod(UpsertDialect dialect) {
        super(METHOD_NAME, dialect, true);
    }

    /**
     * 使用给定的方言和填充策略创建新的 UpsertBatchMethod。
     *
     * @param dialect      用于 SQL 生成的 Upsert 方言
     * @param fillStrategy SQL 绑定前应用的自动填充策略
     * @since 1.6.0
     */
    public UpsertBatchMethod(UpsertDialect dialect, FillStrategy fillStrategy) {
        super(METHOD_NAME, dialect, true, fillStrategy);
    }
}
