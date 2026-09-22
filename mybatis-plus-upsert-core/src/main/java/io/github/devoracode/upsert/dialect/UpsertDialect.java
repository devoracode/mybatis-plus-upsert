package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * 为特定数据库构建 Upsert SQL 的方言接口。实现类须无状态且线程安全。
 *
 * @author devoracode
 * @since 1.0.0
 */
public interface UpsertDialect {

    /**
     * 构建给定元数据的单行 Upsert SQL。
     *
     * @param meta 表名、列名、冲突键等 SQL 生成元数据
     * @return 生成的 SQL 字符串，含 MyBatis 动态标签时不要自带 {@code <script>} 包裹
     */
    String buildUpsertSql(UpsertMeta meta);
}
