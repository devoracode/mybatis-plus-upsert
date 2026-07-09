package io.github.devoracode.upsert.mapper;

import com.baomidou.mybatisplus.core.batch.MybatisBatch;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.override.MybatisMapperProxy;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.MybatisBatchUtils;
import com.baomidou.mybatisplus.core.toolkit.MybatisUtils;
import io.github.devoracode.upsert.injector.UpsertExecutorMethod;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extends {@link BaseMapper} to provide upsert (insert on conflict update) capabilities.
 * The entity class must have at least one field annotated with {@link io.github.devoracode.upsert.annotation.ConflictKey}.
 *
 * @param <T> the entity type
 * @author devoracode
 * @since 1.0.0
 */
public interface UpsertMapper<T> extends BaseMapper<T> {

    /**
     * Inserts a single entity, or updates it if a conflict occurs on the conflict key.
     *
     * @param entity the entity to upsert (must not be null)
     * @return the number of affected rows
     */
    int upsert(@Param("et") T entity);

    /**
     * Batch inserts multiple entities, or updates them if conflicts occur on the conflict key.
     *
     * @param list the list of entities to upsert (must not be null or empty)
     * @return the number of affected rows
     */
    int upsertBatch(@Param("list") List<T> list);

    /**
     * Batch upserts a collection of entities using the default batch size.
     *
     * @param entityList the collection of entities to upsert (may be null or empty)
     * @return list of batch results, or empty list if input is null/empty
     */
    default List<BatchResult> upsert(Collection<T> entityList) {
        return upsert(entityList, Constants.DEFAULT_BATCH_SIZE);
    }

    /**
     * Batch upserts a collection of entities with a custom batch size.
     *
     * @param entityList the collection of entities to upsert (may be null or empty)
     * @param batchSize  the batch size to use (must be positive)
     * @return list of batch results, or empty list if input is null/empty
     */
    default List<BatchResult> upsert(Collection<T> entityList, int batchSize) {
        if (entityList == null || entityList.isEmpty()) {
            return Collections.emptyList();
        }
        MybatisMapperProxy<?> mybatisMapperProxy = MybatisUtils.getMybatisMapperProxy(this);
        SqlSessionFactory sqlSessionFactory = MybatisUtils.getSqlSessionFactory(mybatisMapperProxy);
        MybatisBatch.Method<T> method = new MybatisBatch.Method<>(mybatisMapperProxy.getMapperInterface());
        return MybatisBatchUtils.execute(sqlSessionFactory, entityList,
                method.get(UpsertExecutorMethod.METHOD_NAME, entity -> {
                    Map<String, Object> param = new HashMap<>(4);
                    param.put("et", entity);
                    return param;
                }), batchSize);
    }
}