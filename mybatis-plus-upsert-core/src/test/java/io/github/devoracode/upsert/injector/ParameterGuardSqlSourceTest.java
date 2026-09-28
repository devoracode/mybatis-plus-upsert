package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.test.support.AutoIdEntity;
import io.github.devoracode.upsert.test.support.MultiConflictKeyEntity;
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
 * <p>注入的 {@code upsert} 语句直接接收裸实体；{@code upsert(Collection)} 复用同一语句逐行处理，
 * 集合里的 {@code null} 元素或 {@code null} 冲突键会在该元素进入 SQL 绑定前被同一守卫拒绝。
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

    private ParameterGuardSqlSource guard(SqlSource delegate) {
        return new ParameterGuardSqlSource(delegate, configuration, Collections.singletonList("username"));
    }

    @Test
    void passes_a_real_entity_through_to_the_delegate() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = guard(delegate);

        assertThat(guard.getBoundSql(entityParameter(entity("alice")))).isNotNull();
        // 直接向 SqlSession 传裸实体同样识别
        assertThat(guard.getBoundSql(entity("bob"))).isNotNull();
        assertThat(delegate.calls).isEqualTo(2);
    }

    @Test
    void rejects_null_entity_before_touching_the_delegate() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = guard(delegate);

        assertThatThrownBy(() -> guard.getBoundSql(entityParameter(null)))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
        assertThatThrownBy(() -> guard.getBoundSql(null))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
        assertThat(delegate.calls).isZero();
    }

    @Test
    void rejects_null_conflict_key_before_touching_the_delegate() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = guard(delegate);

        assertThatThrownBy(() -> guard.getBoundSql(entityParameter(entity(null))))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert conflict key must not be null")
                .hasMessageContaining("username");
        assertThat(delegate.calls).isZero();
    }

    @Test
    void rejects_a_null_key_in_a_composite_conflict_key() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = new ParameterGuardSqlSource(delegate, configuration,
                Arrays.asList("tenantId", "bizCode"));
        MultiConflictKeyEntity entity = new MultiConflictKeyEntity();
        entity.setTenantId("tenant-a");
        entity.setBizCode(null);

        assertThatThrownBy(() -> guard.getBoundSql(entity))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert conflict key must not be null")
                .hasMessageContaining("bizCode");
        assertThat(delegate.calls).isZero();
    }

    /** MyBatis 的 ParamMap 对缺失键抛 BindingException，守卫必须把它转成能直接读懂的 UpsertException。 */
    @Test
    void rejects_param_map_without_the_entity_key() {
        ParameterGuardSqlSource guard = guard(new CountingSqlSource());
        ParamMap<Object> parameter = new ParamMap<>();
        parameter.put(Constants.LIST, Collections.singletonList(entity("ivan")));

        assertThatThrownBy(() -> guard.getBoundSql(parameter))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
    }

    /**
     * {@code upsert(Collection)} 集合里的 null 元素复用单行语句时，
     * 不会做整批预扫描，而是在该元素排队执行时被守卫拒绝。
     */
    @Test
    void null_collection_element_is_rejected_as_a_null_entity() {
        CountingSqlSource delegate = new CountingSqlSource();
        ParameterGuardSqlSource guard = guard(delegate);
        List<AutoIdEntity> entities = Arrays.asList(entity("frank"), null);

        // 有实体的那条正常通过
        assertThat(guard.getBoundSql(entityParameter(entities.get(0)))).isNotNull();
        // null 元素作为该行参数传入时被拒，且不进入 SQL 绑定
        assertThatThrownBy(() -> guard.getBoundSql(entityParameter(entities.get(1))))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    void collection_shaped_parameter_is_not_mistaken_for_an_entity() {
        // 单行语句收到 list 参数形态时按实体判断：et 缺失即拒绝，不会误当集合扫描
        ParameterGuardSqlSource guard = guard(new CountingSqlSource());
        Map<String, Object> listParameter = new HashMap<>();
        listParameter.put(Constants.LIST, Collections.singletonList(entity("henry")));

        assertThatThrownBy(() -> guard.getBoundSql(listParameter))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Upsert entity must not be null");
    }
}
