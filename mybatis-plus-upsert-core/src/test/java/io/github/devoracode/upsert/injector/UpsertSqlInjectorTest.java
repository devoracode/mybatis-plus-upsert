package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.devoracode.upsert.dialect.H2UpsertDialect;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UpsertSqlInjectorTest {

    private MybatisConfiguration configuration;
    private UpsertSqlInjector injector;

    @BeforeEach
    void setUp() {
        configuration = new MybatisConfiguration();
        injector = new UpsertSqlInjector(new H2UpsertDialect());
    }

    /**
     * 父类返回的列表不属于我们：可能是按 mapper 缓存的共享列表，直接 add 会污染缓存，
     * 让同一个 mapper 之后取到重复的 upsert 注入。
     */
    @Test
    void caller_mutating_the_returned_list_does_not_affect_later_calls() {
        List<AbstractMethod> first = injector.getMethodList(configuration, UpsertEntity.class, tableInfo());
        int sizeBeforePollution = first.size();
        first.add(null);

        List<AbstractMethod> second = injector.getMethodList(configuration, UpsertEntity.class, tableInfo());

        assertThat(second).hasSize(sizeBeforePollution).doesNotContainNull();
    }

    private TableInfo tableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(UpsertEntity.class.getName());
        return TableInfoHelper.initTableInfo(assistant, UpsertEntity.class);
    }

    static class UpsertEntity {
        private Long id;
        private String code;
    }
}
