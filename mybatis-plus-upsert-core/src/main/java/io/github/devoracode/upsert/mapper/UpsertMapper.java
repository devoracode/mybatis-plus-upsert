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

/*
 * Extend this interface to gain upsert capabilities.
 * The entity class must have at least one @ConflictKey field.
 */
public interface UpsertMapper<T> extends BaseMapper<T> {

    int upsert(@Param("et") T entity);

    int upsertBatch(@Param("list") List<T> list);

    default List<BatchResult> upsert(Collection<T> entityList) {
        if (entityList == null || entityList.isEmpty()) {
            return Collections.emptyList();
        }
        return upsert(entityList, Constants.DEFAULT_BATCH_SIZE);
    }

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
