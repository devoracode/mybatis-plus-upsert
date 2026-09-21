package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * 为特定数据库构建 Upsert SQL 的方言接口。实现类须无状态且线程安全。
 *
 * <p>SQL 只在启动时（动态数据源下为某个方言首次使用时）生成一次并烘焙进 MyBatis
 * {@code SqlSource}，由 {@code MappedStatement} 持有——与 MyBatis-Plus 一致，
 * {@code SqlSource} 本身就是缓存，方言无需另存 SQL 字符串。
 *
 * @author devoracode
 * @since 1.0.0
 */
public interface UpsertDialect {

    /**
     * 构建给定元数据的单行 Upsert SQL。
     *
     * <p>方言只需实现这一个方法：批量写入不另建多行 SQL，{@code upsert(Collection)}
     * 复用本方法产出的语句，由 MyBatis-Plus 的 {@code MybatisBatch} 在
     * {@code ExecutorType.BATCH} 下逐条提交。
     *
     * @param meta 表名、列名、冲突键等 SQL 生成元数据
     * @return 生成的 SQL 字符串，含 MyBatis 动态标签时不要自带 {@code <script>} 包裹
     */
    String buildUpsertSql(UpsertMeta meta);
}
