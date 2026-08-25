package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * 为特定数据库方言构建 Upsert SQL 语句的接口。
 * 实现类无状态且线程安全。
 *
 * <p>SQL 在启动时生成一次（单数据源）或按每个方言首次使用时生成（动态数据源），
 * 并烘焙到 MyBatis {@code SqlSource} 中，缓存于 {@code MappedStatement}。
 * 这与 MyBatis-Plus 的原生方式一致：{@code SqlSource} 本身即充当缓存，
 * 无需额外的 SQL 字符串缓存。
 *
 * @author devoracode
 * @since 1.0.0
 */
public interface UpsertDialect {

    /**
     * 为给定元数据构建单行 Upsert SQL。
     *
     * @param meta 包含表名、列名、冲突键等信息的 Upsert 元数据
     * @return 生成的 SQL 字符串
     */
    String buildUpsertSql(UpsertMeta meta);

    /**
     * 为给定元数据构建批量 Upsert SQL。
     *
     * @param meta 包含表名、列名、冲突键等信息的 Upsert 元数据
     * @return 生成的 SQL 字符串
     */
    String buildUpsertBatchSql(UpsertMeta meta);
}
