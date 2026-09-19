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

    private static Map<String, Object> listParameter(List<?> list) {
        Map<String, Object> parameter = new HashMap<>();
        parameter.put(Constants.LIST, list);
        parameter.put(ParamNameResolver.GENERIC_NAME_PREFIX + 1, list);
        return parameter;
    }

    private static AutoIdEntity entity(String username) {
        return AutoIdEntity.builder().username(username).email(username + "@example.com").build();
    }

    @Test
    void single_path_passes_a_real_entity_through_to_the_delegate() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(delegate, false);

        assertThat(guard.getBoundSql(entityParameter(entity("alice")))).isNotNull();
        // 直接向 SqlSession 传裸实体同样识别
        assertThat(guard.getBoundSql(entity("bob"))).isNotNull();
        assertThat(delegate.calls).isEqualTo(2);
    }

    @Test
    void single_path_rejects_null_entity_before_touching_the_delegate() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(delegate, false);

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
    void single_path_rejects_param_map_without_the_entity_key() {
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(new CountingSqlSource(), false);
        ParamMap<Object> parameter = new ParamMap<>();
        parameter.put(Constants.LIST, Collections.singletonList(entity("ivan")));

        assertThatThrownBy(() -> guard.getBoundSql(parameter))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
    }

    @Test
    void batch_path_passes_a_full_list_through_to_the_delegate() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(delegate, true);

        assertThat(guard.getBoundSql(listParameter(Arrays.asList(entity("carol"), entity("dave"))))).isNotNull();
        // 裸集合同样识别
        assertThat(guard.getBoundSql(Arrays.asList(entity("erin")))).isNotNull();
        assertThat(delegate.calls).isEqualTo(2);
    }

    @Test
    void batch_path_rejects_null_and_non_collection_parameter() {
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(new CountingSqlSource(), true);

        assertThatThrownBy(() -> guard.getBoundSql(listParameter(null)))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("must be a non-null collection")
                .hasMessageContaining("null");
        assertThatThrownBy(() -> guard.getBoundSql(null))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("must be a non-null collection");
        ParamMap<Object> withoutList = new ParamMap<>();
        withoutList.put(Constants.ENTITY, entity("judy"));
        assertThatThrownBy(() -> guard.getBoundSql(withoutList))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("must be a non-null collection");
    }

    @Test
    void batch_path_rejects_empty_collection() {
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(new CountingSqlSource(), true);

        assertThatThrownBy(() -> guard.getBoundSql(listParameter(Collections.emptyList())))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void batch_path_rejects_null_element_and_reports_its_index() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(delegate, true);

        assertThatThrownBy(() -> guard.getBoundSql(
                listParameter(Arrays.asList(entity("frank"), entity("grace"), null))))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("null element at index 2");
        assertThat(delegate.calls).isZero();
    }

    @Test
    void batch_guard_does_not_reject_the_single_path_shape_and_vice_versa() {
        // 单行语句收到多行参数形态时按实体判断：et 缺失即拒绝，不会误当集合扫描
        ParameterGuardSqlSource single = new ParameterGuardSqlSource(new CountingSqlSource(), false);
        assertThatThrownBy(() -> single.getBoundSql(listParameter(Collections.singletonList(entity("henry")))))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
    }
}
