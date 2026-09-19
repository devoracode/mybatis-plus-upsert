package io.github.devoracode.upsert.mapper;

import com.baomidou.mybatisplus.core.batch.MybatisBatch;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.override.MybatisMapperProxy;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.MybatisBatchUtils;
import com.baomidou.mybatisplus.core.toolkit.MybatisUtils;
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
     * <p>{@code IdType.AUTO} 主键走数据库生成键回填（MyBatis-Plus 原生
     * {@code Jdbc3KeyGenerator} 机制），语句执行成功后实体主键被填充；
     * 序列主键复用 MyBatis-Plus 的 sequence key generator。
     * 注意冲突更新（UPDATE）路径下数据库不产生新键，回填值取决于驱动对
     * generated keys 的返回（MySQL 驱动通常返回既有主键）。
     *
     * @param entity 待 upsert 的实体对象（不能为 null）
     * @return 受影响行数
     */
    int upsert(@Param("et") T entity);

    /**
     * 批量插入多条实体，若发生冲突键冲突则执行更新（单条 SQL，多行 VALUES）。
     *
     * <p><strong>不承诺主键回填</strong>：多行 SQL 的 generated keys 与行的对应关系
     * 受数据库和 JDBC 驱动差异影响（MySQL 下冲突更新行返回的键数不固定，PostgreSQL
     * 冲突更新路径不返回 RETURNING 行），因此本方法不对实体主键做任何填充，
     * {@code IdType.AUTO} 实体的主键在调用后仍为 null。需要回填请使用 {@link #upsert}
     * 或 {@link #upsert(Collection, int)}。
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
     * <p>因逐条提交的是单行 SQL，{@code IdType.AUTO} 主键回填按 MyBatis-Plus 原生
     * 批量 insert 的同一机制工作：生成键在批次 {@code flushStatements} 时写回各实体，
     * 而非每条语句 {@code execute} 返回时立即可见。
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
                    // 必须用 ParamMap 而非普通 HashMap：Jdbc3KeyGenerator 只对 ParamMap 识别
                    // “带 @Param 的单参数”，从而把生成主键写回实体本身而不是 map 的键
                    ParamMap<T> parameter = new ParamMap<>();
                    parameter.put(Constants.ENTITY, entity);
                    parameter.put(ParamNameResolver.GENERIC_NAME_PREFIX + 1, entity);
                    return parameter;
                }), batchSize);
    }
}
