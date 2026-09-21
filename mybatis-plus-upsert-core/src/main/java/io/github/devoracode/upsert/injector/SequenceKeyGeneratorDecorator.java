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
 * 序列主键的 KeyGenerator 装饰器：把 MyBatis-Plus 取到的号搬运到实体本身。
 *
 * <p>取号完全由 MP 完成（{@link com.baomidou.mybatisplus.core.metadata.TableInfoHelper#genKeyGenerator}
 * 注册的 {@code !selectKey} 语句 + 容器里的 {@code IKeyGenerator} Bean），本类只补写回那一步：
 * SelectKeyGenerator 通过<em>参数对象</em>的 {@link MetaObject} 写主键，而 Upsert 的实体被
 * {@code @Param("et")} 包在 {@code ParamMap} 里，对 Map 写 {@code id} 只新增一个键、
 * 落不到实体上（MP 原生 {@code insert(T entity)} 没有 {@code @Param}，不受影响），
 * 结果绑定 {@code #{et.id}} 时仍是 NULL。
 *
 * <p>所以委托执行后把参数映射上的该属性值搬回 {@code et} 对应的实体；参数本就是裸实体时
 * MP 已直接写在实体上，无需搬运。
 *
 * @author devoracode
 * @since 1.6.2
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
        Object entity = parameterMap.containsKey(Constants.ENTITY) ? parameterMap.get(Constants.ENTITY) : null;
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
