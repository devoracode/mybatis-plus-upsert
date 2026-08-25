package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.dialect.UpsertDialect;

/**
 * 单行 Upsert SQL 注入方法。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertMethod extends AbstractUpsertMethod {

    /**
     * 在 Mapper 中注册的方法名。
     */
    public static final String METHOD_NAME = UpsertMethodNames.UPSERT;

    /**
     * 使用给定的方言和默认填充策略创建新的 UpsertMethod。
     *
     * @param dialect 用于 SQL 生成的 Upsert 方言
     */
    public UpsertMethod(UpsertDialect dialect) {
        super(METHOD_NAME, dialect, false);
    }

    /**
     * 使用给定的方言和填充策略创建新的 UpsertMethod。
     *
     * @param dialect      用于 SQL 生成的 Upsert 方言
     * @param fillStrategy SQL 绑定前应用的自动填充策略
     * @since 1.6.0
     */
    public UpsertMethod(UpsertDialect dialect, FillStrategy fillStrategy) {
        super(METHOD_NAME, dialect, false, fillStrategy);
    }
}
