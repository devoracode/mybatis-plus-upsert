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
 * 继承自 {@link BaseMapper} 的 Upsert（插入即更新 / insert on conflict update）操作扩展接口。
 * 实体类必须至少有一个字段使用 {@link io.github.devoracode.upsert.annotation.ConflictKey} 注解标记冲突键。
 *
 * @param <T> 实体类型
 * @author devoracode
 * @since 1.0.0
 */
public interface UpsertMapper<T> extends BaseMapper<T> {

    /**
     * 插入单条实体，若发生冲突键冲突则执行更新。
     *
     * @param entity 待 upsert 的实体对象（不能为 null）
     * @return 受影响行数
     */
    int upsert(@Param("et") T entity);

    /**
     * 批量插入多条实体，若发生冲突键冲突则执行更新（单条 SQL，多行 VALUES）。
     *
     * @param list 待批量 upsert 的实体列表（不能为 null 或空）
     * @return 受影响行数
     */
    int upsertBatch(@Param("list") List<T> list);

    /**
     * 使用默认批次大小逐条 upsert 实体集合（内部委托给 {@code upsert(entity, DEFAULT_BATCH_SIZE)}）。
     *
     * @param entityList 待 upsert 的实体集合（可为 null 或空）
     * @return 批量执行结果列表；输入为 null 或空时返回空列表
     */
    default List<BatchResult> upsert(Collection<T> entityList) {
        return upsert(entityList, Constants.DEFAULT_BATCH_SIZE);
    }

    /**
     * 按指定批次大小逐条 upsert 实体集合，使用 {@code MybatisBatch} 执行以提高性能。
     * 每个批次中的每行单独调用单条 upsert SQL，返回各行 BatchResult，
     * 可从中精确获取每行的实际受影响行数（MySQL 下 0=更新/1=插入，其他数据库为实际行数）。
     *
     * @param entityList 待 upsert 的实体集合（可为 null 或空）
     * @param batchSize  批次大小（必须为正整数）
     * @return 批量执行结果列表；输入为 null 或空时返回空列表
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