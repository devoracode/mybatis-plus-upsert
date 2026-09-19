package io.github.devoracode.upsert.test.scope;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前缀 b_ 的 Spring 上下文，与 {@link UpsertContextPrefixATest} 在同一 JVM 内
 * 共存（两个上下文都持有 ContextScopeEntity 的映射）：各自注入的 upsert SQL
 * 必须各自使用自己上下文的表名。两个测试类的先后顺序不应影响结果。
 */
@SpringBootTest(classes = io.github.devoracode.upsert.test.TestApplication.class,
        properties = "mybatis-plus.global-config.db-config.table-prefix=b_")
class UpsertContextPrefixBTest extends AbstractContextPrefixRenderTest {

    @Test
    void upsert_sql_uses_own_context_table_name() {
        assertThat(renderUpsertSql())
                .contains("b_context_scope_entity")
                .doesNotContain("a_context_scope_entity");
    }
}
