package io.github.devoracode.upsert.test.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.test.TestApplication;
import io.github.devoracode.upsert.test.support.KeySeqUserEntity;
import io.github.devoracode.upsert.test.support.KeySeqUserMapper;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 序列主键（{@code @KeySequence}）的 Upsert 运行时测试，H2 序列模拟
 * PostgreSQL / Oracle 的 identity / sequence 主键策略。
 *
 * <p>要证明的不是"本库也会生成主键"，而是主键值确实取自数据库序列、
 * 并且这条链路完整复用了 MyBatis-Plus 既有的 selectKey 机制：
 * 由 MP 的 {@link TableInfoHelper#genKeyGenerator} 注册
 * {@code <语句>!selectKey} 语句并挂上 {@link SelectKeyGenerator}，
 * 取号 SQL 来自容器里注册的 {@code IKeyGenerator} Bean；本库至多在生成的
 * {@code SelectKeyGenerator} 外面包一层装饰器，把号从命名参数映射搬回实体。
 *
 * <p>序列号不会随事务回滚，且 {@code CREATE SEQUENCE} 从 1000 起，
 * 因此断言只看"不小于 1000""互不相同"，不钉死具体数字。
 */
@SpringBootTest(classes = TestApplication.class)
@Transactional
@Rollback
class UpsertSequenceIdTest {

    @Autowired
    private KeySeqUserMapper keySeqUserMapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    private MybatisConfiguration configuration() {
        return (MybatisConfiguration) sqlSessionFactory.getConfiguration();
    }

    private MappedStatement statement(String method) {
        return configuration().getMappedStatement(KeySeqUserMapper.class.getName() + "." + method, false);
    }

    private static KeySeqUserEntity user(String username, String email) {
        return KeySeqUserEntity.builder().username(username).email(email).build();
    }

    @BeforeEach
    void clean() {
        keySeqUserMapper.delete(null);
    }

    @Test
    void single_upsert_reuses_the_mp_select_key_generator_for_sequence_ids() {
        MappedStatement upsert = statement("upsert");

        // 取号由 MP 注册的 !selectKey 语句负责；本库只在其外面套一层把号写回实体的
        // 装饰器（SequenceKeyGeneratorDecorator，包私有），所以这里只断言挂载点：
        // 语句上配了 keyProperty，且取号语句确实由 MP 注册、SQL 来自容器的 IKeyGenerator Bean。
        assertThat(upsert.getKeyProperties()).containsExactly("id");
        assertThat(upsert.getKeyGenerator().getClass().getName())
                .as("主键生成器只能是本库对 MP 生成器的包装，不能是 MyBatis 自带类型")
                .startsWith("io.github.devoracode.upsert.injector.");
        // MP 为序列主键注册的取号语句，本库不另外拼序列 SQL
        assertThat(statement("upsert" + SelectKeyGenerator.SELECT_KEY_SUFFIX).getBoundSql(null).getSql())
                .isEqualTo("SELECT NEXT VALUE FOR seq_key_seq_user");
        assertThat(statement(UpsertMethodNames.UPSERT_EXECUTOR).getKeyProperties())
                .containsExactly("id");
        assertThat(configuration().hasStatement(
                KeySeqUserMapper.class.getName() + "." + UpsertMethodNames.UPSERT_EXECUTOR
                        + SelectKeyGenerator.SELECT_KEY_SUFFIX, false))
                .as("逐条提交路径同样复用 MP 的取号语句")
                .isTrue();
    }

    @Test
    void single_upsert_backfills_the_number_taken_from_the_database_sequence() {
        KeySeqUserEntity user = user("seq-alice", "alice@example.com");
        assertThat(user.getId()).isNull();

        keySeqUserMapper.upsert(user);

        // 取自数据库序列（CREATE SEQUENCE 从 1000 起），而不是内存里编出来的值
        assertThat(user.getId()).isNotNull().isGreaterThanOrEqualTo(1000L);
        KeySeqUserEntity stored = keySeqUserMapper.selectById(user.getId());
        assertThat(stored).isNotNull();
        assertThat(stored.getUsername()).isEqualTo("seq-alice");
    }

    @Test
    void successive_upserts_take_distinct_sequence_numbers() {
        KeySeqUserEntity first = user("seq-bob", "bob@example.com");
        KeySeqUserEntity second = user("seq-carol", "carol@example.com");

        keySeqUserMapper.upsert(first);
        keySeqUserMapper.upsert(second);

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(keySeqUserMapper.selectById(first.getId()).getEmail()).isEqualTo("bob@example.com");
        assertThat(keySeqUserMapper.selectById(second.getId()).getEmail()).isEqualTo("carol@example.com");
    }

    /**
     * 冲突更新分支上序列号照样会被取走（selectKey 在语句执行前运行），但落库的行仍是原主键：
     * "实体上出现了一个主键值"不能推出"插入了一条新记录"。
     */
    @Test
    void conflict_update_keeps_the_stored_primary_key_even_though_a_new_number_is_taken() {
        KeySeqUserEntity inserted = user("seq-dave", "old@example.com");
        keySeqUserMapper.upsert(inserted);
        Long originalId = inserted.getId();

        KeySeqUserEntity conflicting = user("seq-dave", "new@example.com");
        keySeqUserMapper.upsert(conflicting);

        assertThat(conflicting.getId()).isNotNull().isNotEqualTo(originalId);
        List<KeySeqUserEntity> rows = keySeqUserMapper.selectList(null);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(originalId);
        assertThat(rows.get(0).getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void collection_upsert_backfills_numbers_row_by_row() {
        List<KeySeqUserEntity> users = Arrays.asList(
                user("seq-erin", "erin@example.com"),
                user("seq-frank", "frank@example.com"));

        keySeqUserMapper.upsert(users);

        assertThat(users).allSatisfy(u -> assertThat(u.getId()).isNotNull().isGreaterThanOrEqualTo(1000L));
        assertThat(users.get(0).getId()).isNotEqualTo(users.get(1).getId());
        for (KeySeqUserEntity user : users) {
            assertThat(keySeqUserMapper.selectById(user.getId()).getUsername()).isEqualTo(user.getUsername());
        }
    }

    /**
     * 多行 VALUES 语句一条取号 SQL 只能得到一个值，无法逐行分配，因此该路径不接序列：
     * 主键必须由调用方提供，本库也不会用序列值覆盖它。
     */
    @Test
    void multi_row_upsert_batch_never_allocates_sequence_numbers() {
        KeySeqUserEntity first = KeySeqUserEntity.builder().id(500001L).username("seq-grace").email("g@example.com").build();
        KeySeqUserEntity second = KeySeqUserEntity.builder().id(500002L).username("seq-henry").email("h@example.com").build();

        int rows = keySeqUserMapper.upsertBatch(Arrays.asList(first, second));

        assertThat(rows).isGreaterThanOrEqualTo(0);
        assertThat(first.getId()).isEqualTo(500001L);
        assertThat(second.getId()).isEqualTo(500002L);
        assertThat(keySeqUserMapper.selectList(null)).hasSize(2);
    }
}
