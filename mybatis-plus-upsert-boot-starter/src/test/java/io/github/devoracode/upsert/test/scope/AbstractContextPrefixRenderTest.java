package io.github.devoracode.upsert.test.scope;

import io.github.devoracode.upsert.test.support.ContextScopeEntity;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * 两个 Spring 上下文（不同 table-prefix）对同一实体类各自注入、各自渲染的公共骨架。
 *
 * <p>刻意只渲染 SQL 不执行：验证目标是"注入期的元数据来自哪个上下文"，
 * 与数据库无关，也不需要为两种前缀各建一张表。
 * {@code upsert} 语句的 SqlSource 在各自上下文启动注入时即用该上下文的
 * UpsertMeta 烘焙完成，因此断言与两个测试类的执行顺序无关。
 */
abstract class AbstractContextPrefixRenderTest {

    private static final String UPSERT_STATEMENT_ID =
            "io.github.devoracode.upsert.test.support.ContextScopeMapper.upsert";

    @Autowired
    SqlSessionFactory sqlSessionFactory;

    /**
     * 用本上下文的 Configuration 渲染单行 upsert SQL。
     * 参数按 MyBatis 代理层的形态包成 {@code {et: entity}}。
     */
    protected String renderUpsertSql() {
        MappedStatement ms = sqlSessionFactory.getConfiguration().getMappedStatement(UPSERT_STATEMENT_ID);
        Map<String, Object> param = new HashMap<>();
        param.put("et", ContextScopeEntity.builder()
                .username("alice")
                .email("alice@example.com")
                .build());
        BoundSql boundSql = ms.getBoundSql(param);
        return boundSql.getSql();
    }
}
