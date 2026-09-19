package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.test.support.UserEntity;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RoutingUpsertSqlSource} 的 SqlSource 缓存键测试。
 *
 * <p>核心回归点：缓存键必须落在<em>方言实例</em>上，而不是方言类名上。否则会出现在
 * <ul>
 *   <li>同一方言类的两个不同实例（两个数据源各引用一个带不同配置的自定义方言 Bean）</li>
 *   <li>简单类名相同、包名不同的两个方言类</li>
 * </ul>
 * 场景下，后到的数据源直接命中前一个数据源生成的 SQL，拿到结构完全不同的
 * ON DUPLICATE KEY / ON CONFLICT / MERGE 语句。
 *
 * <p>同时验证缓存收益仍在（同一实例只构建一次）、并发首次访问只构建一次、
 * 以及方言实例每次都在变化时缓存不会无限增长。
 */
class RoutingUpsertSqlSourceCacheTest {

    private MybatisConfiguration configuration;
    private XMLLanguageDriver languageDriver;
    private UpsertMeta meta;

    @BeforeEach
    void setUp() {
        configuration = new MybatisConfiguration();
        languageDriver = new XMLLanguageDriver();
        // 桩方言只读取表名，无需完整元数据
        meta = UpsertMeta.builder()
                .tableName("t_user")
                .entityClass(UserEntity.class)
                .build();
    }

    private RoutingUpsertSqlSource routingSource(DynamicUpsertDialect dynamicDialect, boolean batch) {
        return new RoutingUpsertSqlSource(configuration, languageDriver, dynamicDialect, meta, batch, UserEntity.class);
    }

    private static String renderedSql(RoutingUpsertSqlSource source) {
        return source.getBoundSql(new Object()).getSql();
    }

    // --- 缓存键按方言实例区分 ---

    @Test
    void same_dialect_class_different_instances_do_not_share_cached_sql() {
        CountingDialect dsA = new CountingDialect("ds-a");
        CountingDialect dsB = new CountingDialect("ds-b");
        StubDynamicDialect dynamic = new StubDynamicDialect(dsA);
        RoutingUpsertSqlSource source = routingSource(dynamic, false);

        assertThat(renderedSql(source)).contains("ds-a");

        // 路由到同类的另一个实例：旧实现按类名缓存，这里会错误地复用 ds-a 的 SQL
        dynamic.current = dsB;
        assertThat(renderedSql(source)).contains("ds-b");

        // 路由回 dsA 时命中自己的缓存条目
        dynamic.current = dsA;
        assertThat(renderedSql(source)).contains("ds-a");

        assertThat(dsA.singleCalls.get()).isEqualTo(1);
        assertThat(dsB.singleCalls.get()).isEqualTo(1);
    }

    @Test
    void dialects_with_identical_simple_class_names_do_not_collide() {
        PackageOne.SameSimpleNameDialect one = new PackageOne.SameSimpleNameDialect();
        PackageTwo.SameSimpleNameDialect two = new PackageTwo.SameSimpleNameDialect();
        // 前置条件：两个方言类的 simple name 完全相同，只有包/类不同
        assertThat(one.getClass().getSimpleName()).isEqualTo(two.getClass().getSimpleName());

        StubDynamicDialect dynamic = new StubDynamicDialect(one);
        RoutingUpsertSqlSource source = routingSource(dynamic, false);

        assertThat(renderedSql(source)).contains("package-one");
        dynamic.current = two;
        assertThat(renderedSql(source)).contains("package-two");
    }

    @Test
    void equal_dialect_instances_are_allowed_to_share_cached_sql() {
        // 方言若重写 equals/hashCode 声明两个实例等价，则共享同一 SQL 是正确行为
        EquitableDialect first = new EquitableDialect("shared");
        EquitableDialect second = new EquitableDialect("shared");
        StubDynamicDialect dynamic = new StubDynamicDialect(first);
        RoutingUpsertSqlSource source = routingSource(dynamic, false);

        assertThat(renderedSql(source)).contains("shared");
        dynamic.current = second;
        assertThat(renderedSql(source)).contains("shared");

        assertThat(first.singleCalls.get() + second.singleCalls.get()).isEqualTo(1);
    }

    // --- 缓存收益 ---

    @Test
    void repeated_calls_on_same_dialect_instance_build_sql_once() {
        CountingDialect dialect = new CountingDialect("ds-a");
        RoutingUpsertSqlSource source = routingSource(new StubDynamicDialect(dialect), false);

        for (int i = 0; i < 50; i++) {
            assertThat(renderedSql(source)).contains("ds-a");
        }
        assertThat(dialect.singleCalls.get()).isEqualTo(1);
    }

    @Test
    void batch_and_single_statements_keep_their_own_sql() {
        CountingDialect dialect = new CountingDialect("ds-a");
        StubDynamicDialect dynamic = new StubDynamicDialect(dialect);
        RoutingUpsertSqlSource single = routingSource(dynamic, false);
        RoutingUpsertSqlSource batch = routingSource(dynamic, true);

        // 两条语句共享同一个动态方言实例，但 batch 维度必须区分开
        for (int i = 0; i < 5; i++) {
            assertThat(renderedSql(single)).contains("single-ds-a").doesNotContain("batch-ds-a");
            assertThat(renderedSql(batch)).contains("batch-ds-a").doesNotContain("single-ds-a");
        }
        assertThat(dialect.singleCalls.get()).isEqualTo(1);
        assertThat(dialect.batchCalls.get()).isEqualTo(1);
    }

    @Test
    void concurrent_first_access_builds_sql_once_per_dialect_instance() throws Exception {
        SlowDialect dialect = new SlowDialect("ds-a");
        RoutingUpsertSqlSource source = routingSource(new StubDynamicDialect(dialect), false);

        int threads = 8;
        CyclicBarrier startLine = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    startLine.await();
                    return renderedSql(source);
                }));
            }
            for (Future<String> result : results) {
                assertThat(result.get()).contains("ds-a");
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(dialect.singleCalls.get()).isEqualTo(1);
    }

    // --- 缓存上限 ---

    @Test
    void dialect_resolved_per_call_never_fills_the_cache_and_stays_correct() throws Exception {
        RoutingUpsertSqlSource source = routingSource(new AlwaysNewDialect(), false);

        int calls = 200;
        for (int i = 0; i < calls; i++) {
            // 每次都是新的方言实例：即使缓存旁路，SQL 仍必须与当次解析出的方言匹配
            assertThat(renderedSql(source)).contains("fresh-" + i);
        }
        assertThat(cachedEntryCount(source)).isBetween(1, 64);
    }

    private static int cachedEntryCount(RoutingUpsertSqlSource source) throws Exception {
        Field field = RoutingUpsertSqlSource.class.getDeclaredField("sqlSourceCache");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(source)).size();
    }

    // --- 测试桩 ---

    /**
     * 生成带自身标记的 SQL，并记录每种语句被构建的次数。
     */
    static class CountingDialect implements UpsertDialect {

        final AtomicInteger singleCalls = new AtomicInteger();
        final AtomicInteger batchCalls = new AtomicInteger();
        private final String marker;

        CountingDialect(String marker) {
            this.marker = marker;
        }

        @Override
        public String buildUpsertSql(UpsertMeta meta) {
            singleCalls.incrementAndGet();
            return sql("single-" + marker);
        }

        @Override
        public String buildUpsertBatchSql(UpsertMeta meta) {
            batchCalls.incrementAndGet();
            return sql("batch-" + marker);
        }

        private static String sql(String tag) {
            return "INSERT INTO t_user ( username ) VALUES ( #{et.username} ) /* " + tag + " */";
        }
    }

    /**
     * 构建 SQL 时略微耗时，用于放大并发首次访问的竞争窗口。
     */
    private static final class SlowDialect extends CountingDialect {

        SlowDialect(String marker) {
            super(marker);
        }

        @Override
        public String buildUpsertSql(UpsertMeta meta) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.buildUpsertSql(meta);
        }
    }

    /**
     * 声明自身等价（同 marker 即 equals）的方言，且不与 {@link CountingDialect} 等价。
     */
    private static final class EquitableDialect extends CountingDialect {

        private final String marker;

        EquitableDialect(String marker) {
            super(marker);
            this.marker = marker;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EquitableDialect && marker.equals(((EquitableDialect) other).marker);
        }

        @Override
        public int hashCode() {
            return Objects.hash(EquitableDialect.class, marker);
        }
    }

    /**
     * 动态方言桩：可在测试中切换当前解析出的方言实例。
     */
    private static final class StubDynamicDialect implements DynamicUpsertDialect {

        volatile UpsertDialect current;

        StubDynamicDialect(UpsertDialect current) {
            this.current = current;
        }

        @Override
        public UpsertDialect getCurrentDialect() {
            return current;
        }

        @Override
        public String buildUpsertSql(UpsertMeta meta) {
            return current.buildUpsertSql(meta);
        }

        @Override
        public String buildUpsertBatchSql(UpsertMeta meta) {
            return current.buildUpsertBatchSql(meta);
        }
    }

    /**
     * 每次调用都返回一个新方言实例的病态实现。
     */
    private static final class AlwaysNewDialect implements DynamicUpsertDialect {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public UpsertDialect getCurrentDialect() {
            return new CountingDialect("fresh-" + sequence.getAndIncrement());
        }

        @Override
        public String buildUpsertSql(UpsertMeta meta) {
            return getCurrentDialect().buildUpsertSql(meta);
        }

        @Override
        public String buildUpsertBatchSql(UpsertMeta meta) {
            return getCurrentDialect().buildUpsertBatchSql(meta);
        }
    }

    /**
     * 两个包外宿主类下的同名方言类，用于复现按 simple name 缓存时的键冲突。
     */
    static final class PackageOne {
        static final class SameSimpleNameDialect extends CountingDialect {
            SameSimpleNameDialect() {
                super("package-one");
            }
        }
    }

    static final class PackageTwo {
        static final class SameSimpleNameDialect extends CountingDialect {
            SameSimpleNameDialect() {
                super("package-two");
            }
        }
    }
}
