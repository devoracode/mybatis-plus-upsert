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

    /**
     * UPDATE SET 中是否回退为 {@code #{param.property}} 参数引用而非行引用。
     * 仅当字段参与 UPDATE 但被排除在 INSERT 之外时为 true
     * （例如 {@code insertStrategy = NEVER} 的可更新字段）——
     * 此时行引用（{@code new.col} / {@code EXCLUDED.col} / {@code src.col} / {@code VALUES(col)}）
     * 指向插入行中不存在的列，参数引用是唯一能表达"更新为实体当前值"的方式。
     *
     * @since 1.6.1
     */
    private final boolean paramRef;
}
