package io.github.devoracode.upsert.autoconfigure;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.exception.UpsertException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源环境下的 {@link DynamicUpsertDialect} 实现。
 *
 * <p>维护数据源名称到 {@link UpsertDialect} 实例的映射。
 * 运行时通过检查当前数据源上下文（{@link DynamicDataSourceContextHolder}）
 * 解析正确的方言。如果没有活动上下文，则回退到已配置的主数据源。
 *
 * @author devoracode
 * @since 1.2.0
 */
@Slf4j
public class DynamicUpsertDialectImpl implements DynamicUpsertDialect {

    private final Map<String, UpsertDialect> dialectMap = new ConcurrentHashMap<>();
    /**
     * 主数据源。
     */
    @Getter
    @Setter
    private volatile String primary;

    /**
     * 为指定数据源注册方言。
     *
     * @param dataSourceName 数据源名称（不能为 null）
     * @param dialect UpsertDialect 实例（不能为 null）
     */
    public void addDialect(String dataSourceName, UpsertDialect dialect) {
        dialectMap.put(dataSourceName, dialect);
    }

    /**
     * 返回已注册方言映射的不可修改视图。
     *
     * @return 方言映射（数据源名称 → UpsertDialect）
     */
    public Map<String, UpsertDialect> getDialectMap() {
        return Collections.unmodifiableMap(dialectMap);
    }

    @Override
    public UpsertDialect getCurrentDialect() {
        String dataSourceName = DynamicDataSourceContextHolder.peek();
        if(dataSourceName == null) {
            dataSourceName = primary;
        }
        UpsertDialect dialect = dialectMap.get(dataSourceName);
        if (dialect == null) {
            throw new UpsertException("No upsert dialect configured for data source '" + dataSourceName + "'");
        }
        if (log.isDebugEnabled()) {
            log.debug("Using dialect {} for data source {}", dialect.getClass().getSimpleName(), dataSourceName);
        }
        return dialect;
    }

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        return getCurrentDialect().buildUpsertSql(meta);
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        return getCurrentDialect().buildUpsertBatchSql(meta);
    }
}