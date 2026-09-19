package io.github.devoracode.upsert.core;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Upsert SQL 生成所需的元数据容器。
 * 包含构建 Upsert 语句所需的全部信息：表名、列名、冲突键、更新列以及字段到列的映射。
 *
 * <p>实例由 {@link UpsertMetaParser} 基于注入期传入的 {@code TableInfo} 创建；
 * 解析器无缓存，每个 {@code Configuration} 各自解析、各自持有实例。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Getter
@Builder
public class UpsertMeta {

    /**
     * 数据库表名。
     */
    private final String tableName;

    /**
     * INSERT 子句的列名列表。
     */
    private final List<String> insertColumns;

    /**
     * INSERT 子句的 Java 字段名列表（与 insertColumns 一一对应）。
     */
    private final List<String> insertFields;

    /**
     * 构成冲突键的列名列表（用于 ON CONFLICT / ON DUPLICATE KEY / MERGE ON 子句）。
     */
    private final List<String> conflictColumns;

    /**
     * UPDATE 子句的列名列表（不含冲突键列）。
     */
    private final List<String> updateColumns;

    /**
     * UPDATE 子句的 Java 字段名列表（与 updateColumns 一一对应）。
     */
    private final List<String> updateFields;

    /**
     * INSERT 列的字段元数据列表（包含动态标记和空值检查标记）。
     */
    private final List<FieldMeta> insertFieldMetas;

    /**
     * UPDATE 列的字段元数据列表（包含动态标记和空值检查标记）。
     */
    private final List<FieldMeta> updateFieldMetas;

    /**
     * Java 字段名到数据库列名的映射。
     */
    private final Map<String, String> fieldToColumnMap;

    /**
     * 此元数据所解析自的实体类。
     * 作为 SQL 缓存键的一部分，确保映射到相同表名但结构不同的实体
     * （例如跨数据源场景）拥有各自独立的缓存 Upsert SQL，而非共享一个错误的 SQL。
     */
    private final Class<?> entityClass;
}
