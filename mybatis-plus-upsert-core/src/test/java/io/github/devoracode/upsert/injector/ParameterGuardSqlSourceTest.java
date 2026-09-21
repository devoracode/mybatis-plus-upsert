package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.test.support.AutoIdEntity;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ParameterGuardSqlSource} 的参数形态守卫测试。
 *
 * <p>MyBatis 本身不会拦下 {@code null} 实体：它把所有列绑成 {@code NULL} 后照常执行，
 * 由数据库返回一个间接错误、甚至写入一条全空记录。守卫的作用是在 SQL 绑定之前
 * 把这类调用变成一条能直接读懂的 {@link UpsertException}，
 * 且拒绝时绝不能把参数交给委托 SqlSource。
 *
 * <p>注入的两条语句（{@code upsert} 与 {@code upsertExecutor}）参数形态相同，
 * 因此守卫只有一种形态；{@code upsert(Collection)} 集合里的 {@code null} 元素
 * 表现为一次 {@code et} 为 null 的 {@code upsertExecutor} 调用，由同一份逻辑拒绝。
 */
class ParameterGuardSqlSourceTest {

    private final MybatisConfiguration configuration = new MybatisConfiguration();

    /** 记录是否被访问，用于证明守卫在拒绝时不进入 SQL 绑定。 */
    private static final class CountingSqlSource implements SqlSource {
        private int calls;

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            calls++;
            return new BoundSql(new MybatisConfiguration(), "SELECT 1", Collections.emptyList(), parameterObject);
        }
    }

    private static Map<String, Object> entityParameter(Object entity) {
        Map<String, Object> parameter = new HashMap<>();
        parameter.put(Constants.ENTITY, entity);
        parameter.put(ParamNameResolver.GENERIC_NAME_PREFIX + 1, entity);
        return parameter;
    }

    private static AutoIdEntity entity(String username) {
        return AutoIdEntity.builder().username(username).email(username + "@example.com").build();
    }

    @Test
    void passes_a_real_entity_through_to_the_delegate() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(delegate);

        assertThat(guard.getBoundSql(entityParameter(entity("alice")))).isNotNull();
        // 直接向 SqlSession 传裸实体同样识别
        assertThat(guard.getBoundSql(entity("bob"))).isNotNull();
        assertThat(delegate.calls).isEqualTo(2);
    }

    @Test
    void rejects_null_entity_before_touching_the_delegate() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(delegate);

        assertThatThrownBy(() -> guard.getBoundSql(entityParameter(null)))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
        assertThatThrownBy(() -> guard.getBoundSql(null))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
        assertThat(delegate.calls).isZero();
    }

    /** MyBatis 的 ParamMap 在键缺失时抛 BindingException，守卫必须先判断键存在再取值。 */
    @Test
    void rejects_param_map_without_the_entity_key() {
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(new CountingSqlSource());
        ParamMap<Object> parameter = new ParamMap<>();
        parameter.put(Constants.LIST, Collections.singletonList(entity("ivan")));

        assertThatThrownBy(() -> guard.getBoundSql(parameter))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
    }

    /**
     * {@code upsert(Collection)} 集合里的 null 元素对应的正是一次 {@code et} 为 null 的
     * {@code upsertExecutor} 调用：集合形态不会被当作整批扫描，而是在该元素排队执行时拒绝。
     */
    @Test
    void null_collection_element_is_rejected_as_a_null_entity() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(delegate);
        List<AutoIdEntity> entities = Arrays.asList(entity("frank"), null);

        // 有实体的那条正常通过
        assertThat(guard.getBoundSql(entityParameter(entities.get(0)))).isNotNull();
        // null 元素作为 upsertExecutor 的 et 传入时被拒，且不进入 SQL 绑定
        assertThatThrownBy(() -> guard.getBoundSql(entityParameter(entities.get(1))))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    void collection_shaped_parameter_is_not_mistaken_for_an_entity() {
        // 单行语句收到 list 参数形态时按实体判断：et 缺失即拒绝，不会误当集合扫描
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(new CountingSqlSource());
        Map<String, Object> listParameter = new HashMap<>();
        listParameter.put(Constants.LIST, Collections.singletonList(entity("henry")));

        assertThatThrownBy(() -> guard.getBoundSql(listParameter))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
    }
}
