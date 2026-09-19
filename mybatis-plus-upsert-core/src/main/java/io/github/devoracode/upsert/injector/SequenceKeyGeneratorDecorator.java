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
 * 注册的 {@code !selectKey} 语句 + 用户容器里的 {@code IKeyGenerator} Bean），
 * 本类只补一步写回：MyBatis 的 SelectKeyGenerator 通过<em>参数对象</em>的
 * {@link MetaObject} 写回主键，而 Upsert 的实体是被 {@code @Param("et")} 包进
 * {@code ParamMap} 的，对 Map 写 {@code id} 只会新增一个 map 键、不会落到实体上
 * （MP 原生 {@code insert(T entity)} 的入参没有 {@code @Param}，所以原生路径不受影响）。
 * 实体拿不到号，绑定 {@code #{et.id}} 时就是 {@code NULL}。
 *
 * <p>因此本类在委托执行之后，把参数映射里该主键属性上的值写回 {@code et} 对应的实体。
 * 只在参数确实是命名参数映射时搬运；直接向 {@code SqlSession} 传裸实体时，
 * MP 原样写在实体上，无需处理。
 *
 * @author devoracode
 * @since 1.6.2
 */
final class SequenceKeyGeneratorDecorator implements KeyGenerator {

    private final KeyGenerator delegate;
    private final Configuration configuration;
    private final String keyProperty;

    /**
     * @param delegate     MP 注册的序列取号生成器
     * @param configuration MyBatis 配置，用于反射参数对象
     * @param keyProperty  实体主键属性名（同时也是参数映射里的键名）
     */
    SequenceKeyGeneratorDecorator(KeyGenerator delegate, Configuration configuration, String keyProperty) {
        this.delegate = delegate;
        this.configuration = configuration;
        this.keyProperty = keyProperty;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        delegate.processBefore(executor, ms, stmt, parameter);
        copyKeyToEntity(parameter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        delegate.processAfter(executor, ms, stmt, parameter);
        copyKeyToEntity(parameter);
    }

    /**
     * 把取号结果从参数映射搬到实体上；参数不是命名映射或没有取到号时不做任何事。
     *
     * @param parameter 语句参数对象
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
