package io.github.devoracode.upsert.test.injector;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.dialect.*;
import io.github.devoracode.upsert.injector.UpsertSqlInjector;
import io.github.devoracode.upsert.test.support.AutoIdEntity;
import io.github.devoracode.upsert.test.support.AutoIdUserMapper;
import io.github.devoracode.upsert.test.support.InputIdUserMapper;
import io.github.devoracode.upsert.test.support.NoIdUserMapper;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upsert 注入方法的 MappedStatement 主键回填配置测试。
 *
 * <p>验证与 MyBatis-Plus 原生 {@code Insert} 一致的 KeyGenerator 选择：
 * <ul>
 *   <li>IdType.AUTO → Jdbc3KeyGenerator + keyProperty/keyColumn；</li>
 *   <li>注入器只产出一条单行语句，批量写入复用同一条语句，不存在多行语句；</li>
 *   <li>IdType.INPUT 或无主键实体 → NoKeyGenerator；</li>
 *   <li>上述选择对方言一致（MySQL / PostgreSQL / Oracle / SQL Server / H2 均相同）。</li>
 * </ul>
 */
class UpsertKeyGeneratorInjectionTest {

    private MybatisConfiguration configuration;

    @BeforeEach
    void setup() {
        configuration = new MybatisConfiguration();
        injectMapper(AutoIdUserMapper.class);
        injectMapper(InputIdUserMapper.class);
    }

    /*
     * 模拟 MyBatis-Plus 运行时的注入流程：先 setCurrentNamespace 设定命名空间，
     * 再执行 SQL 注入器，语句 id 形如 <mapper 全限定名>.upsert。
     */
    private void injectMapper(Class<?> mapperClass) {
        injectMapper(configuration, mapperClass, new MysqlUpsertDialect());
    }

    private void injectMapper(MybatisConfiguration cfg, Class<?> mapperClass, UpsertDialect dialect) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace(mapperClass.getName());
        new UpsertSqlInjector(dialect).inspectInject(assistant, mapperClass);
    }

    private MappedStatement statement(Class<?> mapper, String method) {
        return configuration.getMappedStatement(mapper.getName() + "." + method, false);
    }

    @Test
    void auto_id_single_upsert_uses_jdbc3_key_generator() {
        MappedStatement ms = statement(AutoIdUserMapper.class, "upsert");
        assertThat(ms).isNotNull();
        assertThat(ms.getKeyGenerator()).isInstanceOf(Jdbc3KeyGenerator.class);
        assertThat(ms.getKeyProperties()).containsExactly("id");
        assertThat(ms.getKeyColumns()).containsExactly("id");
    }

    @Test
    void only_single_row_statements_are_injected() {
        // 批量写入复用单行的 upsert 语句，不再注入独立的 upsertExecutor 或多行 upsertBatch 语句
        for (String method : UpsertMethodNames.ALL) {
            assertThat(configuration.hasStatement(AutoIdUserMapper.class.getName() + "." + method, false))
                    .as("injected statement %s", method).isTrue();
        }
        assertThat(configuration.hasStatement(AutoIdUserMapper.class.getName() + ".upsertExecutor", false))
                .isFalse();
        assertThat(configuration.hasStatement(AutoIdUserMapper.class.getName() + ".upsertBatch", false))
                .isFalse();
    }

    @Test
    void input_id_uses_no_key_generator() {
        assertThat(statement(InputIdUserMapper.class, "upsert").getKeyGenerator())
                .isInstanceOf(NoKeyGenerator.class);
    }

    @Test
    void entity_without_primary_key_uses_no_key_generator() {
        injectMapper(NoIdUserMapper.class);
        MappedStatement ms = statement(NoIdUserMapper.class, "upsert");
        assertThat(ms).isNotNull();
        assertThat(ms.getKeyGenerator()).isInstanceOf(NoKeyGenerator.class);
        assertThat(ms.getKeyProperties()).isNullOrEmpty();
    }

    /*
     * 取键机制与方言无关（跟随 MP 原生 insert），因此各受支持数据库的单行语句
     * 都应配置 Jdbc3KeyGenerator。
     */
    @Test
    void key_generator_choice_is_identical_across_dialects() {
        UpsertDialect[] dialects = {
                new MysqlUpsertDialect(),
                new MysqlLegacyUpsertDialect(),
                new PostgresUpsertDialect(),
                new OracleUpsertDialect(),
                new SqlServerUpsertDialect(),
                new H2UpsertDialect(),
        };
        for (UpsertDialect dialect : dialects) {
            MybatisConfiguration cfg = new MybatisConfiguration();
            injectMapper(cfg, AutoIdUserMapper.class, dialect);
            for (String method : UpsertMethodNames.ALL) {
                MappedStatement ms = cfg.getMappedStatement(
                        AutoIdUserMapper.class.getName() + "." + method, false);
                assertThat(ms.getKeyGenerator())
                        .as("dialect %s statement %s", dialect.getClass().getSimpleName(), method)
                        .isInstanceOf(Jdbc3KeyGenerator.class);
                assertThat(ms.getKeyProperties()).containsExactly("id");
                assertThat(ms.getKeyColumns()).containsExactly("id");
            }
        }
    }

    @Test
    void auto_id_single_insert_columns_exclude_id() {
        // AUTO 主键不进入 INSERT 列（由数据库生成），这是 generated keys 回填的前提
        AutoIdEntity entity = AutoIdEntity.builder().username("u1").email("u1@example.com").build();
        String sql = statement(AutoIdUserMapper.class, "upsert")
                .getBoundSql(entity).getSql().replaceAll("\\s+", " ");
        assertThat(sql).startsWith("INSERT INTO t_auto_user ( username, email )");
        assertThat(sql).endsWith("ON DUPLICATE KEY UPDATE email = new.email");
    }
}
