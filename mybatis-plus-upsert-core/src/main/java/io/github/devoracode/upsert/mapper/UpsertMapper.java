package io.github.devoracode.upsert.mapper;

import com.baomidou.mybatisplus.core.batch.MybatisBatch;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.override.MybatisMapperProxy;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.MybatisBatchUtils;
import com.baomidou.mybatisplus.core.toolkit.MybatisUtils;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.injector.UpsertExecutorMethod;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 在 MyBatis-Plus {@link BaseMapper} 之上增加 Upsert（存在则更新、不存在则插入）能力的扩展接口。
 *
 * <p>实体至少要有一个 {@link io.github.devoracode.upsert.annotation.ConflictKey} 字段，否则本接口的
 * 语句不会为该 Mapper 注入。动态列、主键回填、事务边界与批量的部分成功语义见 README
 * 「字段动态判断」「主键回填」「批量 Upsert 的实现」三节。
 *
 * @param <T> 实体类型
 * @author devoracode
 * @since 1.0.0
 */
public interface UpsertMapper<T> extends BaseMapper<T> {

    /**
     * 插入实体；冲突键命中已有行时改为更新该行。
     *
     * @param entity 待 upsert 的实体，为 null 时抛 {@link UpsertException}
     * @return 受影响行数（MySQL 下 1=插入、2=更新、0=值未变化，其他数据库不区分插入与更新）
     */
    int upsert(@Param("et") T entity);

    /**
     * 用 MyBatis-Plus 的默认批次大小逐条 upsert 实体集合，等价于 {@code upsert(entityList, DEFAULT_BATCH_SIZE)}。
     *
     * @param entityList 待 upsert 的实体集合；为 null 或空时不执行任何 SQL
     * @return 各批次的执行结果，{@code getUpdateCounts()} 是该批次逐行的受影响行数
     */
    default List<BatchResult> upsert(Collection<T> entityList) {
        return upsert(entityList, Constants.DEFAULT_BATCH_SIZE);
    }

    /**
     * 按指定批次大小逐条 upsert 实体集合。
     *
     * @param entityList 待 upsert 的实体集合；为 null 或空时不执行任何 SQL，含 null 元素时抛 {@link UpsertException}
     * @param batchSize  批次大小，必须为正整数，否则由 MyBatis-Plus 在写入任何一行之前拒绝
     * @return 各批次的执行结果，{@code getUpdateCounts()} 是该批次逐行的受影响行数
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
                    // 必须用 ParamMap 而非普通 HashMap：Jdbc3KeyGenerator 只对 ParamMap 识别
                    // “带 @Param 的单参数”，从而把生成主键写回实体本身而不是 map 的键
                    ParamMap<T> parameter = new ParamMap<>();
                    parameter.put(Constants.ENTITY, entity);
                    parameter.put(ParamNameResolver.GENERIC_NAME_PREFIX + 1, entity);
                    return parameter;
                }), batchSize);
    }
}
