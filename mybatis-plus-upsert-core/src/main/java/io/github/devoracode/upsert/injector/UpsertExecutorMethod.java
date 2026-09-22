package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.dialect.UpsertDialect;

/**
 * 注入内部单行 Upsert 语句的方法，供 {@code upsert(Collection)} 在 BATCH 执行器下逐条复用，
 * 不对外暴露为 Mapper 方法。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertExecutorMethod extends AbstractUpsertMethod {

    /**
     * 在 Mapper 中注册的方法名。
     */
    public static final String METHOD_NAME = UpsertMethodNames.UPSERT_EXECUTOR;

    public UpsertExecutorMethod(UpsertDialect dialect) {
        super(METHOD_NAME, dialect);
    }

    /**
     * @param fillStrategy SQL 绑定前应用的自动填充策略
     * @since 1.6.0
     */
    public UpsertExecutorMethod(UpsertDialect dialect, FillStrategy fillStrategy) {
        super(METHOD_NAME, dialect, fillStrategy);
    }
}
