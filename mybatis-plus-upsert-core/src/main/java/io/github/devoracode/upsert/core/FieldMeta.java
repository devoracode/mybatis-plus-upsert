package io.github.devoracode.upsert.core;

import lombok.Builder;
import lombok.Getter;

/**
 * Upsert 操作中标单个字段/列的元数据。
 * 包含动态 SQL 生成所需的信息：列名、属性名，以及基于空值/空字符串检查的条件包含标志。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Getter
@Builder
public class FieldMeta {

    /**
     * 数据库列名。
     */
    private final String column;

    /**
     * 实体类中的 Java 字段（属性）名称。
     */
    private final String property;

    /**
     * 此字段是否需要动态 SQL 处理（即基于空值/空字符串的条件包含）。
     * 当为 true 时，字段在生成的 MyBatis XML 中会被 {@code <if>} 标签包裹。
     */
    private final boolean dynamic;

    /**
     * 是否在空值检查之外额外检查空字符串。
     * 仅在 {@link #dynamic} 为 true 且字段类型为 String 时有效。
     */
    private final boolean checkEmpty;
}
