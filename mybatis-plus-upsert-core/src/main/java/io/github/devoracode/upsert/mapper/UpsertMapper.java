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
 * 实体至少要有一个 {@link io.github.devoracode.upsert.annotation.ConflictKey} 字段，
 * 否则本接口的语句不会为该 Mapper 注入。
 *
 * <p><strong>事务边界</strong>：本接口不自行开启或提交事务。{@code upsert(entity)} 在调用方
 * 当前事务内执行，传播行为与 MP 普通 Mapper 方法一致；{@code upsert(Collection)} 使用独立
 * SqlSession 按批次 flush，部分成功语义见其方法注释。
 *
 * @param <T> 实体类型
 * @author devoracode
 * @since 1.0.0
 */
public interface UpsertMapper<T> extends BaseMapper<T> {

    /**
     * 插入实体；冲突键命中已有行时改为更新该行。
     *
     * <p><strong>动态列</strong>：列集合按字段在 MyBatis-Plus 中的 {@code FieldStrategy}
     * 于运行时裁剪（全局默认 {@code NOT_NULL}）——值为 null 的字段既不进 INSERT 也不进
     * UPDATE SET，与 MP 原生 {@code insert}/{@code updateById} 一致。两个例外：冲突键列不受
     * 动态判断影响、始终参与（为 null 时由数据库约束报错，而不是静默从 SQL 中消失）；
     * {@code IdType.AUTO} 主键本就不参与 INSERT。
     *
     * <p><strong>主键回填</strong>：{@code IdType.AUTO} 沿用 MP 原生 {@code Jdbc3KeyGenerator}，
     * 实体拿到的是<em>数据库实际生成</em>的主键而非内存预测值；{@code @KeySequence} 序列主键
     * 复用 MP 的 selectKey 在插入前取号，需按 MP 的要求注册 {@code IKeyGenerator} Bean。
     * {@code INPUT} / {@code ASSIGN_ID} / {@code ASSIGN_UUID} 不配置生成器，
     * 实体主键原样写入、原样保留。
     *
     * <p><strong>命中更新分支时不产生新主键</strong>：各数据库在 UPDATE 分支下是否返回值并不一致
     * （MySQL / H2 通常返回既有主键，PostgreSQL 的 {@code ON CONFLICT DO UPDATE} 不产生生成键，
     * Oracle / SQL Server 的 MERGE 不返回生成键），序列号则早在语句执行前就已取走且不随事务回滚。
     * 因此<strong>不要把"主键有值"当作"这一行是新插入的"的判断依据</strong>；
     * 需要区分插入与更新请看返回的受影响行数，或事后回查。
     *
     * @param entity 待 upsert 的实体，不能为 null（否则抛 {@link UpsertException}）
     * @return 受影响行数（MySQL 下 1=插入、2=更新、0=命中已有行但值未变化；其他数据库为实际行数）
     */
    int upsert(@Param("et") T entity);

    /**
     * 用 MyBatis-Plus 的默认批次大小逐条 upsert 实体集合，等价于 {@code upsert(entityList, DEFAULT_BATCH_SIZE)}。
     *
     * <p>这是本项目的批量写入方式，与 MP 原生 {@code insert(Collection)} 同构：不拼多行
     * {@code VALUES} SQL，而是复用 {@link #upsert} 那条单行语句，由 {@code MybatisBatch} 在
     * {@code ExecutorType.BATCH} 下逐条 {@code addBatch}。因此每一行各有自己的动态列集合，
     * 主键回填也与单条一致，细节见 {@link #upsert(Collection, int)}。
     *
     * @param entityList 待 upsert 的实体集合；为 null 或空时不执行任何 SQL，返回空列表
     * @return 各批次的执行结果，可从 {@code getUpdateCounts()} 读出逐行受影响行数
     */
    default List<BatchResult> upsert(Collection<T> entityList) {
        return upsert(entityList, Constants.DEFAULT_BATCH_SIZE);
    }

    /**
     * 按指定批次大小逐条 upsert 实体集合。
     *
     * <p><strong>主键回填</strong>：逐条提交的是单行 SQL，所以 {@code IdType.AUTO} 与
     * {@code @KeySequence} 主键都按 MP 原生批量 insert 的同一机制回填——生成键在批次
     * {@code flushStatements} 时写回实体（而非每条 {@code execute} 返回时），方法正常返回后
     * 集合中每个实体的主键都已就位。
     *
     * <p><strong>部分成功</strong>：{@code MybatisBatch} 按 {@code batchSize} 分块 flush 并提交，
     * 与 MP 一致也不预扫描集合（{@code null} 元素在轮到它排队执行时才被拒绝），
     * 所以超出首个批次的失败发生时前面的批次可能已经提交。请把整批放在同一事务里，
     * 异常时按整批失败处理；已写进实体却随事务回滚的主键不会自动清空。
     *
     * <p><strong>可见性</strong>：批量执行使用独立 SqlSession，与调用方 SqlSession 的一级缓存
     * 不互通，同一事务内紧接着用 Mapper 查询可能读不到刚写入的数据。
     *
     * @param entityList 待 upsert 的实体集合；为 null 或空时返回空列表（对齐 MP {@code Db#saveBatch}），
     *                   含 null 元素时由语句层的参数守卫抛 {@link UpsertException}
     * @param batchSize  批次大小（必须为正整数；非正整数在写入任何一行之前由 MyBatis-Plus 拒绝，
     *                   抛出的是 MyBatis-Plus 自己的异常而非 {@link UpsertException}）
     * @return 各批次的执行结果，可从 {@code getUpdateCounts()} 读出逐行受影响行数
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
