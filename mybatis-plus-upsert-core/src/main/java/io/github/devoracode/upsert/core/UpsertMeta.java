package io.github.devoracode.upsert.core;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 构建 Upsert 语句所需的元数据：表名、INSERT/UPDATE 列集合、冲突键与字段到列的映射。
 *
 * <p>由 {@link UpsertMetaParser} 基于注入期传入的 {@code TableInfo} 创建；解析器无缓存，
 * 每个 {@code Configuration} 各自解析、各自持有实例。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Getter
@Builder
public class UpsertMeta {

    /** 数据库表名。 */
    private final String tableName;

    /**
     * INSERT 的固定候选列集合，不参与按值裁剪（{@code insertStrategy = NEVER} 的字段已剔除）。
     * 需要按值增减列时改用 {@link #insertFieldMetas}。
     */
    private final List<String> insertColumns;

    /** 与 {@link #insertColumns} 一一对应的 Java 字段名。 */
    private final List<String> insertFields;

    /** 冲突键列名，用于 ON CONFLICT / ON DUPLICATE KEY / MERGE ON 子句。 */
    private final List<String> conflictColumns;

    /** UPDATE SET 的固定候选列集合（不含冲突键列）。 */
    private final List<String> updateColumns;

    /** 与 {@link #updateColumns} 一一对应的 Java 字段名。 */
    private final List<String> updateFields;

    /** 带动态判断信息的 INSERT 字段，方言的列裁剪 {@code <if>} 由它生成。 */
    private final List<FieldMeta> insertFieldMetas;

    /** 带动态判断信息的 UPDATE 字段，UPDATE SET 的 {@code <if>} 由它生成。 */
    private final List<FieldMeta> updateFieldMetas;

    /** Java 字段名到数据库列名的映射。 */
    private final Map<String, String> fieldToColumnMap;

    /**
     * 解析来源的实体类，同时是 SQL 缓存键的一部分：映射到同一表名但结构不同的实体
     * （如跨数据源场景）因此各持自己的 SQL，不会共享一份错误的。
     */
    private final Class<?> entityClass;
}
