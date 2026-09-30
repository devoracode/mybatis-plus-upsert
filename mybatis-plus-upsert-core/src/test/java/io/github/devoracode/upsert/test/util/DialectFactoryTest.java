package io.github.devoracode.upsert.test.util;

import io.github.devoracode.upsert.dialect.*;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.util.DialectFactory;
import io.github.devoracode.upsert.util.DbTypeDetector.DbType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DialectFactoryTest {

    @Test
    void create_returns_correct_dialect_type() {
        assertThat(DialectFactory.create("mysql", false, true)).isInstanceOf(MysqlLegacyUpsertDialect.class);
        assertThat(DialectFactory.create("postgresql", false, true)).isInstanceOf(PostgresUpsertDialect.class);
        assertThat(DialectFactory.create("oracle", false, true)).isInstanceOf(OracleUpsertDialect.class);
        assertThat(DialectFactory.create("h2", false, true)).isInstanceOf(H2UpsertDialect.class);
        assertThat(DialectFactory.create("sqlserver", false, true)).isInstanceOf(SqlServerUpsertDialect.class);
        assertThat(DialectFactory.create("postgres", false, true)).isInstanceOf(PostgresUpsertDialect.class);
        assertThat(DialectFactory.create(DbType.MYSQL, false, true)).isInstanceOf(MysqlLegacyUpsertDialect.class);
        assertThat(DialectFactory.create(DbType.H2, false, true)).isInstanceOf(H2UpsertDialect.class);
    }

    @Test
    void create_by_string_is_case_insensitive() {
        assertThat(DialectFactory.create("MySQL", false, true)).isInstanceOf(MysqlLegacyUpsertDialect.class);
        assertThat(DialectFactory.create("POSTGRESQL", false, true)).isInstanceOf(PostgresUpsertDialect.class);
    }

    @Test
    void mysql_configuration_selects_correct_dialect() {
        assertThat(DialectFactory.create(DbType.MYSQL, false, true)).isInstanceOf(MysqlLegacyUpsertDialect.class);
        assertThat(DialectFactory.create(DbType.MYSQL, true, true)).isInstanceOf(MysqlUpsertDialect.class);
    }

    @Test
    void create_with_unknown_string_throws() {
        assertThatThrownBy(() -> DialectFactory.create("oceanbase", false, true))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Unknown db-type");
    }

    @Test
    void create_caches_instance_per_type() {
        UpsertDialect first = DialectFactory.create("mysql", false, true);
        UpsertDialect second = DialectFactory.create("mysql", false, true);
        assertThat(first).isSameAs(second);

        UpsertDialect legacy1 = DialectFactory.create(DbType.MYSQL, false, true);
        UpsertDialect legacy2 = DialectFactory.create(DbType.MYSQL, false, true);
        UpsertDialect new1 = DialectFactory.create(DbType.MYSQL, true, true);
        UpsertDialect new2 = DialectFactory.create(DbType.MYSQL, true, true);
        assertThat(legacy1).isSameAs(legacy2);
        assertThat(new1).isSameAs(new2);
        assertThat(legacy1).isNotSameAs(new1);
    }

    /**
     * HOLDLOCK 开关会改变 SQL，必须参与实例缓存键，否则先创建的方言会被另一种开关取值复用。
     */
    @Test
    void sqlserver_holdlock_flag_is_part_of_cache_key() {
        UpsertDialect holdlock1 = DialectFactory.create(DbType.SQLSERVER, false, true);
        UpsertDialect holdlock2 = DialectFactory.create(DbType.SQLSERVER, false, true);
        UpsertDialect noHoldlock = DialectFactory.create(DbType.SQLSERVER, false, false);
        assertThat(holdlock1).isSameAs(holdlock2);
        assertThat(holdlock1).isNotSameAs(noHoldlock);
    }

    /**
     * CUSTOM 的方言由应用自己提供 Bean；工厂返回 null 会让忘记判空的调用方
     * 在后面某处炸成 NPE，因此这里直接抛错并指明正确做法。
     */
    @Test
    void custom_db_type_throws_instead_of_returning_null() {
        assertThatThrownBy(() -> DialectFactory.create(DbType.CUSTOM, false, true))
                .isInstanceOf(io.github.devoracode.upsert.exception.UpsertException.class)
                .hasMessageContaining("db-type=custom")
                .hasMessageContaining("UpsertDialect bean");
    }
}
