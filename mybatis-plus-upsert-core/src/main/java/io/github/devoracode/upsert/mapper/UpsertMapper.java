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
 * 继承自 {@link BaseMapper} 的 Upsert（插入即更新 / insert on conflict update）操作扩展接口。
 * 实体类必须至少有一个字段使用 {@link io.github.devoracode.upsert.annotation.ConflictKey} 注解标记冲突键。
 *
 * <p><strong>主键回填范围</strong>：单条 {@code upsert} 与逐条提交的 {@code upsert(Collection)}
 * 回填数据库生成的主键（AUTO 走 generated keys，序列主键走 MyBatis-Plus 的 selectKey）；
 * 单条多行的 {@code upsertBatch} 明确不回填。逐条语义、冲突更新分支的不确定性以及
 * {@code null} 参数的处理见对应方法注释。
 *
 * <p><strong>事务边界</strong>：本接口不自行开启或提交事务，单条与 {@code upsertBatch}
 * 都在调用方当前事务内执行，{@code @Transactional} 传播行为与 MyBatis-Plus 普通 Mapper
 * 方法一致；{@code upsert(Collection)} 走独立 SqlSession 按批次 flush，
 * 部分成功语义见其方法注释。
 *
 * @param <T> 实体类型
 * @author devoracode
 * @since 1.0.0
 */
public interface UpsertMapper<T> extends BaseMapper<T> {

    /**
     * 插入单条实体，若发生冲突键冲突则执行更新。
     *
     * <p><strong>动态列</strong>：列集合按字段在 MyBatis-Plus 中的 {@code FieldStrategy}
     * 于运行时裁剪（全局默认 {@code NOT_NULL}）——字段值为 null 时该列既不进 INSERT
     * 也不进 UPDATE SET，与 MP 原生 {@code insert}/{@code updateById} 行为一致。
     * 两个例外：冲突键列不受动态判断影响，始终出现在 INSERT 与冲突条件中（为 null 时
     * 由数据库约束报错，而不是静默从 SQL 中消失）；主键列跟随 MP 策略，
     * {@code IdType.AUTO} 主键本就不参与 INSERT（见下文回填说明）。
     *
     * <p><strong>主键回填</strong>：{@code IdType.AUTO} 主键走 MyBatis-Plus 原生的
     * {@code Jdbc3KeyGenerator} 机制，值来自 JDBC generated keys，因此实体上拿到的是
     * <em>数据库实际生成</em>的主键，不是内存里的预测值；序列主键
     * （{@code @KeySequence}）复用 MyBatis-Plus 的 selectKey 生成器在插入前取号，
     * 需要按 MyBatis-Plus 的要求注册 {@code IKeyGenerator}。
     *
     * <p>关于"实体主键已有值"：
     * <ul>
     *   <li>{@code IdType.AUTO}：主键列不参与 INSERT，预置值不会写入数据库，
     *       执行后被数据库生成值<em>覆盖</em>；</li>
     *   <li>{@code INPUT} / {@code ASSIGN_ID} / {@code ASSIGN_UUID}：不配置主键生成器，
     *       实体主键原样写入、原样保留。</li>
     * </ul>
     *
     * <p><strong>冲突更新分支不产生新主键</strong>：本方法不是纯 insert，命中已有行时执行的是
     * UPDATE。各数据库在 UPDATE 分支下是否返回值、返回什么值并不一致（MySQL / H2 通常返回既有
     * 主键，PostgreSQL 的 {@code ON CONFLICT DO UPDATE} 在更新分支上不产生生成键（除非语句自带
     * {@code RETURNING}），Oracle 与 SQL Server 的 MERGE 不返回生成键），
     * 因此<strong>不要把"主键有值"当作"这一行是新插入的"
     * 的判断依据</strong>；需要区分插入与更新请看返回的受影响行数，或事后回查。
     * 序列主键更明显：取号发生在语句执行之前，命中已有行时那个号照样被消耗掉（序列号不随事务回滚），
     * 实体上会出现一个新号，而库里那行的主键仍是原值。
     *
     * @param entity 待 upsert 的实体对象（不能为 null，否则抛 {@link UpsertException}）
     * @return 受影响行数（MySQL 下 1=插入、2=更新、0=命中已有行但值未变化；其他数据库为实际行数）
     */
    int upsert(@Param("et") T entity);

    /**
     * 批量插入多条实体，若发生冲突键冲突则执行更新。SQL 形态按方言而定：
     * MySQL / PostgreSQL / SQL Server 为一条多行 {@code VALUES} 语句，
     * Oracle 为一条 {@code MERGE}（行经 {@code UNION ALL} 拼入源子查询），
     * H2 为 {@code ;} 分隔的逐条 {@code MERGE}。
     *
     * <p><strong>固定列集合</strong>：所有行共享同一列清单，不按字段值逐行判空——
     * {@code NOT_NULL} 字段为 null 时会以 NULL 绑定并<em>覆盖</em>数据库原值，
     * 不会像单条 {@link #upsert} 那样跳过该列。需要"批量且逐行动态判断"时
     * 请改用 {@link #upsert(Collection)}。
     *
     * <p><strong>不承诺主键回填</strong>：多行语句只对应一个 JDBC 结果，其 generated keys
     * 与行的对应关系受数据库和驱动差异影响（MySQL 下冲突更新行返回的键数不固定，
     * PostgreSQL 冲突更新路径不返回 RETURNING 行），因此本方法<em>不配置任何主键生成器</em>：
     * 既不会填充实体主键，也不会因取键数量校验失败而抛异常。
     * {@code IdType.AUTO} 实体的主键在调用后仍为 null，需要主键请使用 {@link #upsert}
     * 或 {@link #upsert(Collection, int)}；序列主键（{@code @KeySequence}）同理不在这条路径上
     * 取号——一次 {@code SELECT NEXT VALUE FOR seq} 只出一个值，无法逐行分配，
     * 主键必须由调用方在入参里给好。
     *
     * @param list 待批量 upsert 的实体列表（不能为 null、不能为空、不能含 null 元素，
     *             否则抛 {@link UpsertException}）
     * @return 受影响行数
     */
    int upsertBatch(@Param("list") List<T> list);

    /**
     * 使用默认批次大小逐条 upsert 实体集合（内部委托给 {@code upsert(entityList, DEFAULT_BATCH_SIZE)}）。
     *
     * @param entityList 待 upsert 的实体集合（为 null 或空时返回空列表，不执行任何 SQL）
     * @return 批量执行结果列表；输入为 null 或空时返回空列表
     */
    default List<BatchResult> upsert(Collection<T> entityList) {
        return upsert(entityList, Constants.DEFAULT_BATCH_SIZE);
    }

    /**
     * 按指定批次大小逐条 upsert 实体集合，使用 {@code MybatisBatch} 执行以提高性能。
     * 每个批次中的每行单独调用单条 upsert SQL，返回各批次的 {@link BatchResult}，
     * 可从中读取每行的实际受影响行数（MySQL 下 1=插入、2=更新、0=命中但未变化）。
     *
     * <p><strong>主键回填</strong>：逐条提交的是单行 SQL，因此 {@code IdType.AUTO} 与序列
     * （{@code @KeySequence}）主键都按 MyBatis-Plus 原生批量 insert 的同一机制回填，取的仍是数据库生成值。
     * 生成键在批次 {@code flushStatements} 时写回实体，而不是每条语句 {@code execute} 返回时立即可见，
     * 所以整个方法正常返回后集合中每个实体的主键都已就位——这是<em>逐条</em>语义，
     * 不是"整批一次性拿到全部生成键"。
     *
     * <p><strong>部分成功</strong>：某条语句失败会让该批次刷新抛出异常，此时本方法不做
     * "已回填主键"与"实际落库行"的对账——已写入实体但被回滚的主键不会自动清空。
     * 与 MyBatis-Plus 一致，本方法不预先扫描集合：元素级问题（如 {@code null} 元素）在轮到它排队执行时
     * 才暴露，而 {@code MybatisBatch} 是按 {@code batchSize} 分块 flush 并提交的，
     * 因此超出首个批次的失败发生时前面的批次可能已经提交。请把整批放在同一事务里，
     * 异常时按整批失败处理，不要假定前 N 条一定成功。
     *
     * <p><strong>可见性</strong>：批量执行使用独立的 SqlSession（MyBatis-Plus {@code MybatisBatch}），
     * 与调用方 SqlSession 的一级缓存不互通，同一事务内紧接着用 Mapper 查询可能读不到刚写入的数据。
     *
     * @param entityList 待 upsert 的实体集合（为 null 或空时返回空列表，与 MyBatis-Plus 的
     *                   {@code Db#saveBatch} 一致；含 null 元素时由语句层参数守卫抛
     *                   {@link UpsertException}）
     * @param batchSize  批次大小（必须为正整数，否则由 MyBatis-Plus 抛出异常）
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
