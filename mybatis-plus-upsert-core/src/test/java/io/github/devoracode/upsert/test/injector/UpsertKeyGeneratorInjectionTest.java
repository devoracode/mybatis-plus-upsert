package io.github.devoracode.upsert.test.injector;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upsert 注入方法的 MappedStatement 主键回填配置测试。
 *
 * <p>验证与 MyBatis-Plus 原生 {@code Insert} 一致的 KeyGenerator 选择：
 * <ul>
 *   <li>IdType.AUTO + 单行路径（upsert / upsertExecutor）→ Jdbc3KeyGenerator + keyProperty/keyColumn；</li>
 *   <li>IdType.AUTO + 多行路径（upsertBatch）→ NoKeyGenerator，明确不承诺回填；</li>
 *   <li>IdType.INPUT 或无主键实体 → 所有路径均 NoKeyGenerator；</li>
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
    void auto_id_executor_method_uses_jdbc3_key_generator() {
        // upsert(Collection) 经该语句在 BATCH 执行器下逐条提交单行 SQL，回填配置与单条一致
        MappedStatement ms = statement(AutoIdUserMapper.class, "upsertExecutor");
        assertThat(ms).isNotNull();
        assertThat(ms.getKeyGenerator()).isInstanceOf(Jdbc3KeyGenerator.class);
        assertThat(ms.getKeyProperties()).containsExactly("id");
        assertThat(ms.getKeyColumns()).containsExactly("id");
    }

    @Test
    void auto_id_multi_row_batch_keeps_no_key_generator() {
        // 多行 VALUES SQL 的 generated keys 对应关系不可靠：不配置回填，避免键数校验异常
        MappedStatement ms = statement(AutoIdUserMapper.class, "upsertBatch");
        assertThat(ms).isNotNull();
        assertThat(ms.getKeyGenerator()).isInstanceOf(NoKeyGenerator.class);
        assertThat(ms.getKeyProperties()).isNullOrEmpty();
        assertThat(ms.getKeyColumns()).isNullOrEmpty();
    }

    @Test
    void input_id_all_paths_use_no_key_generator() {
        assertThat(statement(InputIdUserMapper.class, "upsert").getKeyGenerator())
                .isInstanceOf(NoKeyGenerator.class);
        assertThat(statement(InputIdUserMapper.class, "upsertBatch").getKeyGenerator())
                .isInstanceOf(NoKeyGenerator.class);
        assertThat(statement(InputIdUserMapper.class, "upsertExecutor").getKeyGenerator())
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
     * 取键机制与方言无关（跟随 MP 原生 insert），因此各受支持数据库的 AUTO 主键路径
     * 都应配置 Jdbc3KeyGenerator，多行路径都应保持 NoKeyGenerator。
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
            MappedStatement single = cfg.getMappedStatement(AutoIdUserMapper.class.getName() + ".upsert", false);
            MappedStatement multi = cfg.getMappedStatement(AutoIdUserMapper.class.getName() + ".upsertBatch", false);
            assertThat(single.getKeyGenerator())
                    .as("dialect %s single upsert", dialect.getClass().getSimpleName())
                    .isInstanceOf(Jdbc3KeyGenerator.class);
            assertThat(single.getKeyProperties()).containsExactly("id");
            assertThat(single.getKeyColumns()).containsExactly("id");
            assertThat(multi.getKeyGenerator())
                    .as("dialect %s multi-row upsertBatch", dialect.getClass().getSimpleName())
                    .isInstanceOf(NoKeyGenerator.class);
        }
    }

    @Test
    void auto_id_single_insert_columns_exclude_id() {
        // AUTO 主键不进入 INSERT 列（由数据库生成），这是 generated keys 回填的前提
        AutoIdEntity entity = AutoIdEntity.builder().username("u1").email("u1@example.com").build();
        Map<String, Object> parameter = new HashMap<>();
        parameter.put("et", entity);
        String sql = statement(AutoIdUserMapper.class, "upsert")
                .getBoundSql(parameter).getSql().replaceAll("\\s+", " ");
        assertThat(sql).startsWith("INSERT INTO t_auto_user ( username, email )");
        assertThat(sql).endsWith("ON DUPLICATE KEY UPDATE email = new.email");
    }
}
