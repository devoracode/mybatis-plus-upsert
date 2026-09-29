package io.github.devoracode.upsert.test.autoconfigure;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import io.github.devoracode.upsert.autoconfigure.DynamicUpsertDialectImpl;
import io.github.devoracode.upsert.dialect.MysqlUpsertDialect;
import io.github.devoracode.upsert.dialect.PostgresUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.exception.UpsertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 运行期注册方言的扩展点：数据源由配置中心下发或运行期加入时靠它补注册。
 */
class DynamicUpsertDialectImplTest {

    private DynamicUpsertDialectImpl dialect;

    @BeforeEach
    void setUp() {
        dialect = new DynamicUpsertDialectImpl();
        dialect.setPrimary("main");
        DynamicDataSourceContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        DynamicDataSourceContextHolder.clear();
    }

    @Test
    void falls_back_to_primary_when_no_context_is_pushed() {
        UpsertDialect primary = new MysqlUpsertDialect();
        dialect.addDialect("main", primary);

        assertThat(dialect.getCurrentDialect()).isSameAs(primary);
    }

    @Test
    void routes_to_dialect_registered_after_startup() {
        UpsertDialect runtimeAdded = new PostgresUpsertDialect();
        dialect.addDialect("from-nacos", runtimeAdded);

        DynamicDataSourceContextHolder.push("from-nacos");
        try {
            assertThat(dialect.getCurrentDialect()).isSameAs(runtimeAdded);
        } finally {
            DynamicDataSourceContextHolder.clear();
        }
    }

    @Test
    void unregistered_data_source_reports_how_to_register_one() {
        DynamicDataSourceContextHolder.push("from-nacos");
        try {
            assertThatThrownBy(dialect::getCurrentDialect)
                    .isInstanceOf(UpsertException.class)
                    .hasMessageContaining("No upsert dialect configured for data source 'from-nacos'")
                    .hasMessageContaining("addDialect");
        } finally {
            DynamicDataSourceContextHolder.clear();
        }
    }
}
