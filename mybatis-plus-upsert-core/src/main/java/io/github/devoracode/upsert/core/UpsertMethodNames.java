package io.github.devoracode.upsert.core;

import io.github.devoracode.upsert.injector.UpsertSqlInjector;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link UpsertSqlInjector} 注入的语句名常量，与
 * {@link io.github.devoracode.upsert.mapper.UpsertMapper} 中的方法名一一对应。
 *
 * @author devoracode
 * @since 1.0.0
 */
public final class UpsertMethodNames {

    /** 对外暴露的单行 Upsert 语句名。 */
    public static final String UPSERT = "upsert";

    /**
     * 内部单行语句名，不对外暴露为 Mapper 方法：{@code upsert(Collection)} 在
     * BATCH 执行器下逐条复用这条语句。
     */
    public static final String UPSERT_EXECUTOR = "upsertExecutor";

    /** 全部注入语句名。 */
    public static final Set<String> ALL = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(UPSERT, UPSERT_EXECUTOR)));

    private UpsertMethodNames() {
    }
}
