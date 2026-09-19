package io.github.devoracode.upsert.test.scope;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前缀 a_ 的 Spring 上下文：注入的 upsert SQL 必须使用 a_context_scope_entity，
 * 不得被另一上下文（前缀 b_，见 {@link UpsertContextPrefixBTest}，与本类在同一
 * JVM 内共存）的表名污染。
 */
@SpringBootTest(classes = io.github.devoracode.upsert.test.TestApplication.class,
        properties = "mybatis-plus.global-config.db-config.table-prefix=a_")
class UpsertContextPrefixATest extends AbstractContextPrefixRenderTest {

    @Test
    void upsert_sql_uses_own_context_table_name() {
        assertThat(renderUpsertSql())
                .contains("a_context_scope_entity")
                .doesNotContain("b_context_scope_entity");
    }
}
