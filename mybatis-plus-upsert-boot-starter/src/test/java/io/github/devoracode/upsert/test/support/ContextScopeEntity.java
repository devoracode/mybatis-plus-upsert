package io.github.devoracode.upsert.test.support;

import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 双 Spring 上下文测试实体：刻意不标注 @TableName，
 * 表名由所在上下文的 table-prefix + 类名推导，
 * 用于验证两个不同前缀的 ApplicationContext 各自注入正确表名的 Upsert SQL。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContextScopeEntity {

    @ConflictKey
    private String username;

    private String email;
}
