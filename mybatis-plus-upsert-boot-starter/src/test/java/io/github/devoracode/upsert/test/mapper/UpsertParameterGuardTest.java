package io.github.devoracode.upsert.test.mapper;

import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.test.TestApplication;
import io.github.devoracode.upsert.test.support.AutoUserEntity;
import io.github.devoracode.upsert.test.support.AutoUserMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upsert 空值参数在运行时的拒绝行为（H2 MySQL 模式）。
 *
 * <p>这里要钉住的是"错误由谁给出、给得多早"：{@code null} 实体、{@code null}/空集合、
 * 集合内的 {@code null} 元素都必须在 SQL 绑定之前变成明确的 {@link UpsertException}，
 * 而不是让数据库回一个看不出根因的约束错误，或者写入一条全空记录。
 * 每个拒绝用例都会断言表里仍然一行都没有，作为"SQL 从未到达数据库"的证据。
 *
 * <p>{@code upsert(Collection)} 对 {@code null} 与空集合是明确定义的 no-op（返回空列表），
 * 与 MyBatis-Plus 的 {@code Db#saveBatch} 一致；集合里的 {@code null} 元素则在轮到它
 * 排队执行时被单行语句的守卫拒绝，不做整批预扫描。
 */
@SpringBootTest(classes = TestApplication.class)
@Transactional
@Rollback
class UpsertParameterGuardTest {

    @Autowired
    private AutoUserMapper autoUserMapper;

    private static AutoUserEntity user(String username) {
        return AutoUserEntity.builder().username(username).email(username + "@example.com").build();
    }

    /**
     * 执行预期被拒绝的调用，返回其根因。
     *
     * <p>MyBatis/Spring 会把守卫异常包在 {@code PersistenceException} 之下，
     * 因此判断根因而非直接 cause。
     */
    private static Throwable rootCauseOf(ThrowingCallable callable) {
        try {
            callable.call();
        } catch (Throwable thrown) {
            Throwable current = thrown;
            while (current.getCause() != null && current.getCause() != current) {
                current = current.getCause();
            }
            return current;
        }
        throw new AssertionError("Expected the upsert call to be rejected, but it completed normally");
    }

    private void assertRejectedByUpsert(ThrowingCallable callable, String messagePart) {
        assertThat(rootCauseOf(callable)).isInstanceOf(UpsertException.class)
                .hasMessageContaining(messagePart);
        assertThat(autoUserMapper.selectCount(null))
                .as("参数被拒绝时不应有任何一行落库")
                .isZero();
    }

    @BeforeEach
    void clean() {
        autoUserMapper.delete(null);
    }

    @Test
    void null_entity_is_rejected_before_any_sql_runs() {
        assertRejectedByUpsert(() -> autoUserMapper.upsert((AutoUserEntity) null),
                "Upsert entity must not be null");
    }

    /**
     * 逐条路径不做集合预扫描（与 MyBatis-Plus {@code BaseMapper#insert(Collection)} 一致），
     * {@code null} 元素由单行语句的参数守卫拒绝。
     */
    @Test
    void null_element_in_row_by_row_collection_is_rejected_by_the_statement_guard() {
        Collection<AutoUserEntity> withHole = Arrays.asList(user("guard-d"), null, user("guard-f"));

        assertRejectedByUpsert(() -> autoUserMapper.upsert(withHole),
                "Upsert entity must not be null");
    }

    /**
     * 逐条路径把 {@code null} 与空集合当作 no-op：没有行要写，也就不产生任何语句。
     */
    @Test
    void null_and_empty_collections_are_no_ops_on_the_row_by_row_path() {
        assertThat(autoUserMapper.upsert((Collection<AutoUserEntity>) null)).isEmpty();
        assertThat(autoUserMapper.upsert(Collections.<AutoUserEntity>emptyList())).isEmpty();

        assertThat(autoUserMapper.selectCount(null)).isZero();
    }

    /**
     * 批次大小的下限由 MyBatis-Plus 自己把关（在打开批次之前拒绝，消息含 {@code batchSize}），
     * 本库不再重复实现一遍同名校验——因此这里抛出的是 MP 的异常而不是 {@link UpsertException}。
     */
    @Test
    void non_positive_batch_size_is_rejected_by_mybatis_plus_before_writing_any_row() {
        Collection<AutoUserEntity> entities = Collections.singletonList(user("guard-batch-size"));

        assertThatThrownBy(() -> autoUserMapper.upsert(entities, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("batchSize");
        assertThatThrownBy(() -> autoUserMapper.upsert(entities, -1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("batchSize");

        assertThat(autoUserMapper.selectCount(null))
                .as("批次大小非法时不应有任何一行落库")
                .isZero();
    }

    @Test
    void a_healthy_entity_still_persists_after_the_guard_is_in_place() {
        AutoUserEntity entity = user("guard-ok");

        assertThat(autoUserMapper.upsert(entity)).isGreaterThanOrEqualTo(1);
        assertThat(entity.getId()).isNotNull();
        assertThat(autoUserMapper.selectById(entity.getId()).getUsername()).isEqualTo("guard-ok");
    }
}
