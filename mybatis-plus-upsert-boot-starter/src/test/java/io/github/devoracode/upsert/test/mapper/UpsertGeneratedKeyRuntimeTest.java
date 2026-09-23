package io.github.devoracode.upsert.test.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.test.TestApplication;
import io.github.devoracode.upsert.test.support.AutoUserEntity;
import io.github.devoracode.upsert.test.support.AutoUserMapper;
import io.github.devoracode.upsert.test.support.KeylessUserEntity;
import io.github.devoracode.upsert.test.support.KeylessUserMapper;
import io.github.devoracode.upsert.test.support.UserMapper;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自增主键与无主键实体在运行时的一致性测试（H2 MySQL 模式）。
 *
 * <p>覆盖两点：
 * <ul>
 *   <li>{@code keyGenerator}/{@code keyProperty} 不是只配在 SqlSource 上，
 *       而是随 MyBatis-Plus 的 {@code addInsertMappedStatement} 一路走到了
 *       运行期 {@link MappedStatement}，因此这里直接从 {@link SqlSessionFactory}
 *       取出生效的语句来断言；</li>
 *   <li>回填到实体上的主键确实是数据库给出的那个值——用它能查回自己那行。</li>
 * </ul>
 *
 * <p>另外验证没有主键、只有 {@code @ConflictKey} 的实体不会因为没有主键而报错，
 * 以及 {@code upsert(Collection)} 的 {@link BatchResult} 与实体主键互相吻合。
 */
@SpringBootTest(classes = TestApplication.class)
@Transactional
@Rollback
class UpsertGeneratedKeyRuntimeTest {

    @Autowired
    private AutoUserMapper autoUserMapper;

    @Autowired
    private KeylessUserMapper keylessUserMapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    private MappedStatement statement(Class<?> mapper, String method) {
        MybatisConfiguration configuration = (MybatisConfiguration) sqlSessionFactory.getConfiguration();
        return configuration.getMappedStatement(mapper.getName() + "." + method, false);
    }

    private static AutoUserEntity user(String username) {
        return AutoUserEntity.builder().username(username).email(username + "@example.com").build();
    }

    @BeforeEach
    void clean() {
        autoUserMapper.delete(null);
        keylessUserMapper.delete(null);
    }

    @Test
    void key_generator_configuration_reaches_the_live_mapped_statement() {
        MappedStatement upsert = statement(AutoUserMapper.class, UpsertMethodNames.UPSERT);
        assertThat(upsert.getKeyGenerator()).isInstanceOf(Jdbc3KeyGenerator.class);
        assertThat(upsert.getKeyProperties()).containsExactly("id");
        assertThat(upsert.getKeyColumns()).containsExactly("id");

        // 逐条提交复用同一条 upsert 语句；不再有独立的 upsertExecutor statement
        assertThat(sqlSessionFactory.getConfiguration()
                .hasStatement(AutoUserMapper.class.getName() + ".upsertExecutor", false)).isFalse();
        assertThat(sqlSessionFactory.getConfiguration()
                .hasStatement(AutoUserMapper.class.getName() + ".upsertBatch", false)).isFalse();
    }

    @Test
    void backfilled_auto_ids_are_the_ones_the_database_actually_used() {
        AutoUserEntity first = user("gen-alice");
        AutoUserEntity second = user("gen-bob");

        autoUserMapper.upsert(first);
        autoUserMapper.upsert(second);

        // 两个自增值由数据库分配，互不相同，且每个都能查回自己那一行
        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isNotNull();
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(autoUserMapper.selectById(first.getId()).getUsername()).isEqualTo("gen-alice");
        assertThat(autoUserMapper.selectById(second.getId()).getUsername()).isEqualTo("gen-bob");
    }

    /**
     * {@code IdType.AUTO} 的主键不进 INSERT 列，因此实体上预置的值没有意义：
     * 语句执行后由数据库返回值覆盖。需要自己决定主键值请用 {@code IdType.INPUT}。
     */
    @Test
    void preset_auto_id_is_replaced_by_the_database_generated_id() {
        AutoUserEntity user = AutoUserEntity.builder().id(999999L).username("preset-id").email("p@example.com").build();

        autoUserMapper.upsert(user);

        assertThat(user.getId()).isNotEqualTo(999999L);
        assertThat(autoUserMapper.selectById(999999L)).isNull();
        assertThat(autoUserMapper.selectById(user.getId()).getUsername()).isEqualTo("preset-id");
    }

    @Test
    void input_id_entity_gets_no_key_generator_and_keeps_its_own_key() {
        // 用户提供的 String 主键：不创建任何 KeyGenerator（值原样落库见 UpsertAutoIdBackfillTest）
        MappedStatement upsert = statement(UserMapper.class, UpsertMethodNames.UPSERT);
        assertThat(upsert.getKeyGenerator()).isInstanceOf(NoKeyGenerator.class);
        assertThat(upsert.getKeyProperties()).isNullOrEmpty();
    }

    /**
     * 只有 {@code @ConflictKey}、完全没有主键的实体：Upsert 正常插入与更新，
     * 不因为缺少主键而报错，也不配置任何 KeyGenerator。
     */
    @Test
    void entity_without_primary_key_upserts_without_error() {
        assertThat(statement(KeylessUserMapper.class, UpsertMethodNames.UPSERT).getKeyGenerator())
                .isInstanceOf(NoKeyGenerator.class);

        keylessUserMapper.upsert(KeylessUserEntity.builder().username("keyless").email("old@example.com").build());
        keylessUserMapper.upsert(KeylessUserEntity.builder().username("keyless").email("new@example.com").build());

        List<KeylessUserEntity> rows = keylessUserMapper.selectList(null);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void entity_without_primary_key_also_works_on_the_collection_path() {
        keylessUserMapper.upsert(Arrays.asList(
                KeylessUserEntity.builder().username("keyless-a").email("a@example.com").build(),
                KeylessUserEntity.builder().username("keyless-b").email("b@example.com").build()));

        assertThat(keylessUserMapper.selectList(null)).hasSize(2);
    }

    /**
     * BATCH 路径的执行结果与实体主键必须互相吻合。
     *
     * <p>MyBatis 3.5.16 的 {@link BatchResult} 只暴露 updateCounts 与 parameterObjects，
     * 没有 generated keys 视图，因此这里以"参数对象就是同一批实体本身、
     * 且每个实体的主键都能查回对应行"作为一致性判据。
     */
    @Test
    void batch_results_match_the_backfilled_entity_ids() {
        List<AutoUserEntity> users = Arrays.asList(user("batch-ann"), user("batch-bill"), user("batch-cid"));

        List<BatchResult> results = autoUserMapper.upsert(users, 2);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(result -> assertThat(result.getUpdateCounts()).isNotEmpty());
        List<Object> submitted = results.stream()
                .flatMap(result -> result.getParameterObjects().stream())
                .collect(Collectors.toList());
        assertThat(submitted).hasSize(3);
        for (Object parameter : submitted) {
            // 提交给 BATCH 执行器的参数就是实体本身（与 MP 原生批量一致）
            AutoUserEntity entity = (AutoUserEntity) parameter;
            assertThat(users).contains(entity);
            assertThat(entity.getId()).isNotNull();
        }
        for (AutoUserEntity user : users) {
            assertThat(autoUserMapper.selectById(user.getId()).getUsername()).isEqualTo(user.getUsername());
        }
    }
}
