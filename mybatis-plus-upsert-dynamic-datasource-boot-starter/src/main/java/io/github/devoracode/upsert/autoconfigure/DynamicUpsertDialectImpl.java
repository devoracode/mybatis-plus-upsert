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
 * {@link DynamicUpsertDialect} 的动态数据源实现：持有"数据源名 → {@link UpsertDialect}"映射，
 * 运行时按 {@link DynamicDataSourceContextHolder} 的当前栈顶解析；无活动上下文时回退主数据源。
 *
 * @author devoracode
 * @since 1.2.0
 */
@Slf4j
public class DynamicUpsertDialectImpl implements DynamicUpsertDialect {

    private final Map<String, UpsertDialect> dialectMap = new ConcurrentHashMap<>();
    /** 主数据源名，上下文为空时使用。 */
    @Getter
    @Setter
    private volatile String primary;

    /** 为指定数据源注册方言；两者都不能为 null。 */
    public void addDialect(String dataSourceName, UpsertDialect dialect) {
        dialectMap.put(dataSourceName, dialect);
    }

    /**
     * 已注册方言映射的不可修改视图（数据源名 → UpsertDialect）。
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
}