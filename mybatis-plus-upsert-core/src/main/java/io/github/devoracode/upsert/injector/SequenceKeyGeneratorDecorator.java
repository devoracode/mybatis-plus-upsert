package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

import java.sql.Statement;
import java.util.Map;

/**
 * 序列主键的 KeyGenerator 装饰器：取号仍由 MyBatis-Plus 完成，本类在委托执行后把号从
 * 参数映射搬运回 {@code @Param("et")} 包裹的实体本身。
 *
 * @author devoracode
 * @since 1.7.0
 */
final class SequenceKeyGeneratorDecorator implements KeyGenerator {

    private final KeyGenerator delegate;
    private final Configuration configuration;
    private final String keyProperty;

    /**
     * @param keyProperty 实体主键属性名，同时也是参数映射里的键名
     */
    SequenceKeyGeneratorDecorator(KeyGenerator delegate, Configuration configuration, String keyProperty) {
        this.delegate = delegate;
        this.configuration = configuration;
        this.keyProperty = keyProperty;
    }

    @Override
    public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        delegate.processBefore(executor, ms, stmt, parameter);
        copyKeyToEntity(parameter);
    }

    @Override
    public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        delegate.processAfter(executor, ms, stmt, parameter);
        copyKeyToEntity(parameter);
    }

    /**
     * 把取号结果从参数映射搬到实体；参数不是命名映射或没有取到号时什么都不做。
     */
    private void copyKeyToEntity(Object parameter) {
        if (!(parameter instanceof Map)) {
            return;
        }
        Map<?, ?> parameterMap = (Map<?, ?>) parameter;
        Object entity = parameterMap.getOrDefault(Constants.ENTITY, null);
        if (entity == null) {
            return;
        }
        Object takenKey = configuration.newMetaObject(parameter).getValue(keyProperty);
        if (takenKey == null) {
            return;
        }
        MetaObject metaEntity = configuration.newMetaObject(entity);
        if (metaEntity.hasSetter(keyProperty)) {
            metaEntity.setValue(keyProperty, takenKey);
        }
    }
}
