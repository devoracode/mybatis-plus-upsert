package io.github.devoracode.upsert.test.autoconfigure;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import io.github.devoracode.upsert.autoconfigure.DynamicUpsertDialectImpl;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.dialect.PostgresUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.injector.UpsertSqlInjector;
import io.github.devoracode.upsert.test.support.RoutingUserEntity;
import io.github.devoracode.upsert.test.support.RoutingUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 动态数据源下 {@code RoutingUpsertSqlSource} 的 SqlSource 缓存路由测试。
 *
 * <p>使用真实的 {@link DynamicUpsertDialectImpl} 与 {@link DynamicDataSourceContextHolder}
 * 走通"运行时按当前数据源解析方言"的完整链路，重点验证：
 * <ul>
 *   <li>两个数据源引用同一方言类的不同实例时，各自得到自己的 SQL，互不串库；</li>
 *   <li>两个数据源引用不同方言（MySQL 风格 / PostgreSQL）时，语法与路由结果一致；</li>
 *   <li>多线程并发路由到不同数据源时，缓存条目与线程上下文严格匹配。</li>
 * </ul>
 */
class DynamicRoutingSqlSourceCacheTest {

    private static final String SCHEMA_A_DS = "schemaADs";
    private static final String SCHEMA_B_DS = "schemaBDs";
    private static final String POSTGRES_DS = "postgresDs";

    private DynamicUpsertDialectImpl dynamicDialect;
    private SchemaPrefixDialect schemaA;
    private SchemaPrefixDialect schemaB;
    private MappedStatement upsert;

    @BeforeEach
    void injectMapperAgainstDynamicDialect() {
        schemaA = new SchemaPrefixDialect("schema_a");
        schemaB = new SchemaPrefixDialect("schema_b");

        dynamicDialect = new DynamicUpsertDialectImpl();
        // 两个数据源使用同一个方言 Java 类，但是两个不同实例
        dynamicDialect.addDialect(SCHEMA_A_DS, schemaA);
        dynamicDialect.addDialect(SCHEMA_B_DS, schemaB);
        dynamicDialect.addDialect(POSTGRES_DS, new PostgresUpsertDialect());
        dynamicDialect.setPrimary(SCHEMA_A_DS);

        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(RoutingUserMapper.class.getName());
        new UpsertSqlInjector(dynamicDialect, FillStrategy.NONE).inspectInject(assistant, RoutingUserMapper.class);

        upsert = configuration.getMappedStatement(RoutingUserMapper.class.getName() + ".upsert", false);
    }

    @AfterEach
    void clearDataSourceContext() {
        DynamicDataSourceContextHolder.clear();
    }

    @Test
    void same_dialect_class_behind_two_data_sources_resolves_two_different_sqls() {
        assertThat(render(upsert, SCHEMA_A_DS)).contains("schema_a.t_routing_user").doesNotContain("schema_b.");

        // 切换到同类的另一个方言实例：绝不能命中上一个数据源缓存的 SqlSource
        assertThat(render(upsert, SCHEMA_B_DS)).contains("schema_b.t_routing_user").doesNotContain("schema_a.");

        // 再切回第一个数据源，用自己的缓存条目
        assertThat(render(upsert, SCHEMA_A_DS)).contains("schema_a.t_routing_user");

        assertThat(schemaA.singleCalls.get()).isEqualTo(1);
        assertThat(schemaB.singleCalls.get()).isEqualTo(1);
    }

    @Test
    void different_dialects_across_data_sources_use_their_own_syntax() {
        assertThat(render(upsert, SCHEMA_A_DS)).contains("ON DUPLICATE KEY UPDATE");
        assertThat(render(upsert, POSTGRES_DS)).contains("ON CONFLICT (username)")
                .doesNotContain("ON DUPLICATE KEY UPDATE");
        assertThat(render(upsert, SCHEMA_A_DS)).contains("ON DUPLICATE KEY UPDATE")
                .doesNotContain("ON CONFLICT");
    }

    @Test
    void concurrent_threads_routing_to_different_data_sources_get_matching_sql() throws Exception {
        int threads = 8;
        int iterations = 30;
        CyclicBarrier startLine = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final boolean first = t % 2 == 0;
                final String ds = first ? SCHEMA_A_DS : SCHEMA_B_DS;
                final String ownSchema = first ? "schema_a." : "schema_b.";
                final String otherSchema = first ? "schema_b." : "schema_a.";
                results.add(pool.submit((Callable<Boolean>) () -> {
                    startLine.await();
                    DynamicDataSourceContextHolder.push(ds);
                    try {
                        for (int i = 0; i < iterations; i++) {
                            String sql = upsert.getBoundSql(parameter()).getSql();
                            if (!sql.contains(ownSchema) || sql.contains(otherSchema)) {
                                return false;
                            }
                        }
                        return true;
                    } finally {
                        DynamicDataSourceContextHolder.clear();
                    }
                }));
            }
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
        // 每个方言实例各构建一次：并发首次访问没有产生重复 SqlSource
        assertThat(schemaA.singleCalls.get()).isEqualTo(1);
        assertThat(schemaB.singleCalls.get()).isEqualTo(1);
    }

    @Test
    void routing_without_context_falls_back_to_primary_data_source() {
        assertThat(render(upsert, SCHEMA_B_DS)).contains("schema_b.");
        // 上下文里没有压入任何数据源时回退到主数据源
        DynamicDataSourceContextHolder.clear();
        assertThat(renderWithoutContext(upsert)).contains("schema_a.");
    }

    private String render(MappedStatement statement, String dataSourceName) {
        DynamicDataSourceContextHolder.clear();
        DynamicDataSourceContextHolder.push(dataSourceName);
        return statement.getBoundSql(parameter()).getSql();
    }

    private String renderWithoutContext(MappedStatement statement) {
        return statement.getBoundSql(parameter()).getSql();
    }

    private static Map<String, Object> parameter() {
        Map<String, Object> parameter = new HashMap<>();
        parameter.put(Constants.ENTITY, entity());
        return parameter;
    }

    private static RoutingUserEntity entity() {
        return RoutingUserEntity.builder().id(1L).username("alice").email("a@example.com").build();
    }

    /**
     * 带按实例配置的自定义方言：schema 前缀是该实例私有的状态，
     * 因此不同实例必须拥有各自缓存的 SqlSource。
     */
    static final class SchemaPrefixDialect implements UpsertDialect {

        private final AtomicInteger singleCalls = new AtomicInteger();
        private final String schema;

        SchemaPrefixDialect(String schema) {
            this.schema = schema;
        }

        @Override
        public String buildUpsertSql(UpsertMeta meta) {
            singleCalls.incrementAndGet();
            return "INSERT INTO " + schema + "." + meta.getTableName()
                    + " ( id, username, email ) VALUES ( #{et.id}, #{et.username}, #{et.email} )"
                    + " ON DUPLICATE KEY UPDATE email = VALUES(email) /* single-" + schema + " */";
        }
    }
}
