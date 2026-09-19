package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import io.github.devoracode.upsert.test.support.KeySequenceEntity;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SequenceKeyGeneratorDecorator} 的单元测试。
 *
 * <p>这里的桩委托代表 MyBatis-Plus 注册的 {@code SelectKeyGenerator}：取号动作完全发生在
 * 委托内部（本库不参与），委托只会按 MyBatis 的协议把号写进<em>参数对象</em>。装饰器的职责
 * 只有一个——当参数是被 {@code @Param("et")} 包住的命名映射时，把号从映射搬到实体本身。
 */
class SequenceKeyGeneratorDecoratorTest {

    /** 记录调用次数，并按 MyBatis 协议把号写到参数对象上。 */
    private static final class StubGenerator implements KeyGenerator {

        private final Long takenKey;
        private int beforeCalls;
        private int afterCalls;

        StubGenerator(Long takenKey) {
            this.takenKey = takenKey;
        }

        @Override
        public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
            beforeCalls++;
            if (takenKey != null) {
                writeThroughParameter(parameter, takenKey);
            }
        }

        @Override
        public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
            afterCalls++;
            if (takenKey != null) {
                writeThroughParameter(parameter, takenKey);
            }
        }

        /** SelectKeyGenerator 的写回方式：经由参数对象的 MetaObject 写主键属性。 */
        private void writeThroughParameter(Object parameter, Long value) {
            new MybatisConfiguration().newMetaObject(parameter).setValue("id", value);
        }
    }

    private final Configuration configuration = new MybatisConfiguration();

    private Map<String, Object> namedParameter(KeySequenceEntity entity) {
        Map<String, Object> parameter = new HashMap<>();
        parameter.put(Constants.ENTITY, entity);
        parameter.put("param1", entity);
        return parameter;
    }

    @Test
    void number_taken_before_the_statement_lands_on_the_wrapped_entity() {
        KeySequenceEntity entity = new KeySequenceEntity();
        StubGenerator delegate = new StubGenerator(1001L);
        Map<String, Object> parameter = namedParameter(entity);

        new SequenceKeyGeneratorDecorator(delegate, configuration, "id")
                .processBefore(null, null, null, parameter);

        assertThat(delegate.beforeCalls).as("取号仍由 MP 的生成器完成").isEqualTo(1);
        assertThat(entity.getId()).isEqualTo(1001L);
        assertThat(parameter.get("id")).isEqualTo(1001L);
    }

    @Test
    void number_taken_after_the_statement_is_copied_as_well() {
        KeySequenceEntity entity = new KeySequenceEntity();
        StubGenerator delegate = new StubGenerator(1002L);

        new SequenceKeyGeneratorDecorator(delegate, configuration, "id")
                .processAfter(null, null, null, namedParameter(entity));

        assertThat(delegate.afterCalls).isEqualTo(1);
        assertThat(entity.getId()).isEqualTo(1002L);
    }

    /**
     * 直接传裸实体（不经过 Mapper 的命名参数解析）时，MP 本身就写在实体上，
     * 装饰器不得凭空造出映射键。
     */
    @Test
    void bare_entity_parameter_is_left_to_the_delegate() {
        KeySequenceEntity entity = new KeySequenceEntity();
        StubGenerator delegate = new StubGenerator(1003L);

        new SequenceKeyGeneratorDecorator(delegate, configuration, "id")
                .processBefore(null, null, null, entity);

        assertThat(entity.getId()).isEqualTo(1003L);
    }

    @Test
    void nothing_is_overwritten_when_the_delegate_took_no_number() {
        KeySequenceEntity entity = KeySequenceEntity.builder().id(42L).username("u").build();
        StubGenerator delegate = new StubGenerator(null);

        new SequenceKeyGeneratorDecorator(delegate, configuration, "id")
                .processBefore(null, null, null, namedParameter(entity));

        assertThat(entity.getId()).as("未取到号时保留调用方给定的主键").isEqualTo(42L);
    }

    @Test
    void a_named_map_without_an_entity_is_a_no_op() {
        Map<String, Object> parameter = new HashMap<>();
        parameter.put(Constants.LIST, "ignored");
        StubGenerator delegate = new StubGenerator(1004L);

        new SequenceKeyGeneratorDecorator(delegate, configuration, "id")
                .processBefore(null, null, null, parameter);

        assertThat(parameter).containsEntry("id", 1004L);
    }
}
