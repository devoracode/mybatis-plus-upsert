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
        assertThat(DialectFactory.create("mysql", false)).isInstanceOf(MysqlLegacyUpsertDialect.class);
        assertThat(DialectFactory.create("postgresql")).isInstanceOf(PostgresUpsertDialect.class);
        assertThat(DialectFactory.create("oracle")).isInstanceOf(OracleUpsertDialect.class);
        assertThat(DialectFactory.create("h2")).isInstanceOf(H2UpsertDialect.class);
        assertThat(DialectFactory.create(DbType.MYSQL)).isInstanceOf(MysqlLegacyUpsertDialect.class);
        assertThat(DialectFactory.create(DbType.H2)).isInstanceOf(H2UpsertDialect.class);
    }

    @Test
    void create_by_string_is_case_insensitive() {
        assertThat(DialectFactory.create("MySQL")).isInstanceOf(MysqlLegacyUpsertDialect.class);
        assertThat(DialectFactory.create("POSTGRESQL")).isInstanceOf(PostgresUpsertDialect.class);
    }

    @Test
    void mysql_configuration_selects_correct_dialect() {
        assertThat(DialectFactory.create(DbType.MYSQL, false)).isInstanceOf(MysqlLegacyUpsertDialect.class);
        assertThat(DialectFactory.create(DbType.MYSQL, true)).isInstanceOf(MysqlUpsertDialect.class);
    }

    @Test
    void create_with_unknown_string_throws() {
        assertThatThrownBy(() -> DialectFactory.create("oceanbase"))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Unknown db-type");
    }

    @Test
    void create_caches_instance_per_type() {
        UpsertDialect first = DialectFactory.create("mysql");
        UpsertDialect second = DialectFactory.create("mysql");
        assertThat(first).isSameAs(second);

        UpsertDialect legacy1 = DialectFactory.create(DbType.MYSQL, false);
        UpsertDialect legacy2 = DialectFactory.create(DbType.MYSQL, false);
        UpsertDialect new1 = DialectFactory.create(DbType.MYSQL, true);
        UpsertDialect new2 = DialectFactory.create(DbType.MYSQL, true);
        assertThat(legacy1).isSameAs(legacy2);
        assertThat(new1).isSameAs(new2);
        assertThat(legacy1).isNotSameAs(new1);
    }
}
