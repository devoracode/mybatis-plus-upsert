package io.github.devoracode.upsert.core.fill;

import org.apache.ibatis.session.Configuration;

/**
 * Strategy hook invoked before an upsert statement is executed, allowing
 * field auto-filling (e.g. {@code createTime}, {@code updateTime}) to run.
 *
 * <p>Upsert is semantically "insert or update", so both insert-fill and
 * update-fill should be applied. The default implementation bridges to
 * MyBatis-Plus' {@link com.baomidou.mybatisplus.core.handlers.MetaObjectHandler}.
 *
 * <p>The {@link Configuration} is passed by the caller (the upsert {@code SqlSource})
 * because it is already available at execution time and avoids any lazy resolution.
 *
 * @author devoracode
 * @since 1.5.0
 */
public interface UpsertFieldFillHandler {

    /**
     * Apply field filling to an entity before it is upserted.
     *
     * @param entity       the entity instance to fill (must not be null)
     * @param configuration the MyBatis configuration (must not be null)
     */
    void fillBeforeUpsert(Object entity, Configuration configuration);
}
