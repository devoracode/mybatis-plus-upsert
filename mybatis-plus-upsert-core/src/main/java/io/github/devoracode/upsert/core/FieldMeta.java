package io.github.devoracode.upsert.core;

import lombok.Builder;
import lombok.Getter;

/**
 * 单个字段/列的 Upsert 元数据：列名、属性名，以及动态 SQL 生成所需的判空标志。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Getter
@Builder
public class FieldMeta {

    /** 数据库列名。 */
    private final String column;

    /** Java 属性名。 */
    private final String property;

    /** 是否按值动态判断；为 true 时该列在生成的 XML 中由 {@code <if>} 包裹。 */
    private final boolean dynamic;

    /** 判空时是否额外检查空字符串，仅在 {@link #dynamic} 为 true 且字段为 String 时生效。 */
    private final boolean checkEmpty;

    /**
     * UPDATE SET 是否回退为 {@code #{param.property}} 参数引用而非行引用。
     * 仅对参与 UPDATE 却被排除在 INSERT 之外的字段（{@code insertStrategy = NEVER}）为 true：
     * 行引用（{@code new.col} / {@code EXCLUDED.col} / {@code src.col} / {@code VALUES(col)}）
     * 指向的列在插入行里并不存在。
     *
     * @since 1.6.1
     */
    private final boolean paramRef;
}
