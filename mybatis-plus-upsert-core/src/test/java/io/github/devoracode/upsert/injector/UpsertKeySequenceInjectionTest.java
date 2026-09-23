package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.IKeyGenerator;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import io.github.devoracode.upsert.dialect.MysqlUpsertDialect;
import io.github.devoracode.upsert.dialect.OracleUpsertDialect;
import io.github.devoracode.upsert.dialect.PostgresUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.test.support.AutoIdWithSequenceMapper;
import io.github.devoracode.upsert.test.support.KeySequenceEntity;
import io.github.devoracode.upsert.test.support.KeySequenceUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 序列主键（{@code @KeySequence}）的 Upsert 注入测试。
 *
 * <p>核心回归点：序列主键必须原样复用 MyBatis-Plus 的
 * {@link TableInfoHelper#genKeyGenerator}，也就是由 MP 注册
 * {@code <语句名>!selectKey} 语句并返回 {@link SelectKeyGenerator}；取号与写回
 * 全部由 MP 的既有机制完成，本库不参与。
 * 因此断言全部落在
 * <ul>
 *   <li>MP 注册的 SelectKey 语句本身（其 SQL 来自用户注册的 {@link IKeyGenerator}）</li>
 *   <li>KeyGenerator 类型与 keyProperty/keyColumn</li>
 *   <li>未注册 {@link IKeyGenerator} 时按 MP 自身规则退化为普通 INPUT 主键</li>
 * </ul>
 * 三项上。本库不得自行拼序列 SQL 或引入第二套主键协议。
 *
 * <p>注入器只产出 {@code upsert} 一条单行语句，注册一条 SelectKey 语句；
 * {@code upsert(Collection)} 复用同一条语句逐条取号。
 */
class UpsertKeySequenceInjectionTest {

    /** 桩生成器：真实部署里由数据库方言提供（Oracle/PostgreSQL/DB2 …）的 IKeyGenerator Bean。 */
    private static final class StubKeyGenerator implements IKeyGenerator {

        @Override
        public String executeSql(String incrementerName) {
            return "SELECT NEXT VALUE FOR " + incrementerName;
        }

        @Override
        public DbType dbType() {
            return DbType.POSTGRE_SQL;
        }
    }

    private MybatisConfiguration configurationWith(IKeyGenerator... keyGenerators) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = GlobalConfigUtils.getGlobalConfig(configuration);
        globalConfig.getDbConfig().setKeyGenerators(keyGenerators.length == 0
                ? Collections.emptyList()
                : Collections.singletonList(keyGenerators[0]));
        return configuration;
    }

    private MappedStatement inject(MybatisConfiguration configuration, Class<?> mapperClass, UpsertDialect dialect) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(mapperClass.getName());
        new UpsertSqlInjector(dialect).inspectInject(assistant, mapperClass);
        return configuration.getMappedStatement(mapperClass.getName() + ".upsert", false);
    }

    private MappedStatement statement(MybatisConfiguration configuration, String mapper, String method) {
        return configuration.getMappedStatement(mapper + "." + method, false);
    }

    private String selectKeyId(Class<?> mapperClass, String method) {
        return mapperClass.getName() + "." + method + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    }

    @Test
    void sequence_id_single_upsert_reuses_mp_select_key_generator() {
        MybatisConfiguration configuration = configurationWith(new StubKeyGenerator());

        MappedStatement ms = inject(configuration, KeySequenceUserMapper.class, new PostgresUpsertDialect());

        // 取号与写回都由 MP 注册的 SelectKeyGenerator 负责
        assertThat(ms.getKeyGenerator()).isInstanceOf(SelectKeyGenerator.class);
        assertThat(ms.getKeyProperties()).containsExactly("id");
        assertThat(ms.getKeyColumns()).containsExactly("id");
        assertThat(configuration.hasStatement(selectKeyId(KeySequenceUserMapper.class, "upsert"), false)).isTrue();
    }

    @Test
    void mp_registers_the_select_key_statement_with_the_registered_generator_sql() {
        MybatisConfiguration configuration = configurationWith(new StubKeyGenerator());

        inject(configuration, KeySequenceUserMapper.class, new PostgresUpsertDialect());

        MappedStatement selectKey = statement(configuration,
                KeySequenceUserMapper.class.getName(), "upsert" + SelectKeyGenerator.SELECT_KEY_SUFFIX);
        assertThat(selectKey.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
        // SQL 完全来自用户注册的 IKeyGenerator，本库不参与拼装
        assertThat(selectKey.getBoundSql(null).getSql()).isEqualTo("SELECT NEXT VALUE FOR seq_key_seq_user");
        assertThat(selectKey.getResultMaps()).hasSize(1);
        assertThat(selectKey.getResultMaps().get(0).getType()).isEqualTo(Long.class);

        // upsert(Collection) 逐条提交复用同一条 upsert 语句；不再有独立的 upsertExecutor statement
        assertThat(configuration.hasStatement(
                KeySequenceUserMapper.class.getName() + ".upsertExecutor", false)).isFalse();
    }

    @Test
    void sequence_id_reaches_the_insert_column_list() {
        // SelectKey 在语句执行前取号，随后主键值必须出现在 INSERT 列里
        MybatisConfiguration configuration = configurationWith(new StubKeyGenerator());
        MappedStatement ms = inject(configuration, KeySequenceUserMapper.class, new PostgresUpsertDialect());

        KeySequenceEntity entity = KeySequenceEntity.builder().id(77L).username("u").email("u@example.com").build();
        String sql = ms.getBoundSql(entity).getSql().replaceAll("\\s+", " ");

        assertThat(sql).contains("id");
        assertThat(sql).containsIgnoringCase("INSERT INTO t_key_seq_user ( id, username, email )");
    }

    @Test
    void no_multi_row_statement_is_injected() {
        // 序列取号逐行发生在单行语句上；不存在多行 upsertBatch 语句，
        // 也就不会出现"一条 SELECT NEXT VALUE 供整批"的无法分配形态
        MybatisConfiguration configuration = configurationWith(new StubKeyGenerator());
        inject(configuration, KeySequenceUserMapper.class, new PostgresUpsertDialect());

        assertThat(configuration.hasStatement(
                KeySequenceUserMapper.class.getName() + ".upsertBatch", false)).isFalse();
        assertThat(configuration.hasStatement(
                selectKeyId(KeySequenceUserMapper.class, "upsertBatch"), false)).isFalse();
    }

    /**
     * 未注册 {@link IKeyGenerator} Bean 时，序列主键按 MyBatis-Plus 自身的规则退化为
     * 普通 INPUT 主键（MP 只在 {@code DbConfig.keyGenerators} 非空时才读取 {@code @KeySequence}）：
     * 不配置 KeyGenerator、不注册 SelectKey 语句，也不由本库另造一套报错或补号协议。
     */
    @Test
    void missing_key_generator_bean_degrades_to_plain_input_id() {
        MybatisConfiguration configuration = configurationWith();

        MappedStatement ms = inject(configuration, KeySequenceUserMapper.class, new PostgresUpsertDialect());

        assertThat(ms.getKeyGenerator()).isInstanceOf(NoKeyGenerator.class);
        assertThat(ms.getKeyProperties()).isNullOrEmpty();
        assertThat(configuration.hasStatement(selectKeyId(KeySequenceUserMapper.class, "upsert"), false)).isFalse();
    }

    @Test
    void sequence_backed_statement_configuration_is_dialect_independent() {
        UpsertDialect[] dialects = {
                new MysqlUpsertDialect(),
                new PostgresUpsertDialect(),
                new OracleUpsertDialect(),
        };
        for (UpsertDialect dialect : dialects) {
            MybatisConfiguration configuration = configurationWith(new StubKeyGenerator());
            MappedStatement ms = inject(configuration, KeySequenceUserMapper.class, dialect);
            assertThat(ms.getKeyGenerator())
                    .as("dialect %s single upsert", dialect.getClass().getSimpleName())
                    .isInstanceOf(SelectKeyGenerator.class);
            assertThat(configuration.hasStatement(selectKeyId(KeySequenceUserMapper.class, "upsert"), false))
                    .as("dialect %s select key statement", dialect.getClass().getSimpleName())
                    .isTrue();
        }
    }

    /**
     * 与 MP 原生 {@code Insert} 的判断顺序一致：AUTO 优先，
     * 同时标注 {@code @KeySequence} 也只走 generated keys，不注册 SelectKey。
     */
    @Test
    void auto_id_takes_precedence_over_key_sequence() {
        MybatisConfiguration configuration = configurationWith(new StubKeyGenerator());

        MappedStatement ms = inject(configuration, AutoIdWithSequenceMapper.class, new PostgresUpsertDialect());

        assertThat(ms.getKeyGenerator()).isInstanceOf(Jdbc3KeyGenerator.class);
        assertThat(configuration.hasStatement(selectKeyId(AutoIdWithSequenceMapper.class, "upsert"), false))
                .isFalse();
    }
}
