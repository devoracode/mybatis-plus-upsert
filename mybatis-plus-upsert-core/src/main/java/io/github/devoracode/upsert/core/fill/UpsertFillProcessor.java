package io.github.devoracode.upsert.core.fill;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Upsert 操作的 MyBatis-Plus 自动填充无状态执行器，在动态 Upsert SQL 绑定之前被调用。
 *
 * <p><b>为何需要绑定前填充：</b> MyBatis 的 {@code BaseStatementHandler}
 * 在创建 {@code ParameterHandler} 之前就调用 {@code SqlSource.getBoundSql(parameterObject)}，
 * 而 MyBatis-Plus 的原生填充发生在 {@code MybatisParameterHandler} 内部。
 * 动态 {@code <if test="et.x != null">} 条件在 {@code getBoundSql} 阶段求值，
 * 因此一个在绑定时刻为 null 的填充注解字段会被从 SQL 中省略，即使原生
 * {@code insertFill} 之后为其设置了值。此处理器在绑定之前运行，
 * 使其成为 Upsert 语句的权威填充点。
 *
 * <p><b>幂等性：</b> 处理器可能对同一实体被多次调用（例如批量执行时每行 {@code addBatch} 一次）。
 * 严格填充（{@code strictInsertFill}/{@code strictUpdateFill}）会跳过已有值的字段，
 * 因此重复调用是安全的。MyBatis-Plus 的原生 {@code insertFill} 随后还会为单实体
 * （{@code et}）参数再次运行；使用严格填充时该二次调用是空操作。
 *
 * <p><b>集合参数：</b> MyBatis-Plus 3.5.9 的原生参数处理器只为
 * {@code et} 键下的单个实体填充——它不遍历集合参数。因此此处理器同时处理
 * {@code upsertBatch} 的 {@code list} 集合，使其成为批量 Upsert 的唯一填充点。
 *
 * <p>线程安全：所有状态均在每次调用时独立解析；此类无状态且线程安全。
 *
 * @author devoracode
 * @since 1.6.0
 */
public final class UpsertFillProcessor {

    private UpsertFillProcessor() {
    }

    /**
     * 根据给定策略对从参数对象中提取的每个 Upsert 实体应用自动填充。
     *
     * @param parameterObject  映射器调用参数（ParamMap 或实体对象）
     * @param configuration    MyBatis 配置（用于解析 {@code MetaObjectHandler} 并创建 MetaObject）
     * @param strategy         填充策略；{@code null} 视为 {@code NONE}
     */
    public static void fill(Object parameterObject, Configuration configuration, FillStrategy strategy) {
        if (strategy == null || strategy == FillStrategy.NONE || parameterObject == null) {
            return;
        }
        MetaObjectHandler handler = getMetaObjectHandler(configuration);
        if (handler == null) {
            return;
        }
        for (Object entity : extractEntities(parameterObject)) {
            if (entity == null) {
                continue;
            }
            TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
            if (tableInfo == null) {
                continue;
            }
            MetaObject metaObject = configuration.newMetaObject(entity);
            if (tableInfo.isWithInsertFill()) {
                handler.insertFill(metaObject);
            }
            if (strategy == FillStrategy.INSERT_UPDATE && tableInfo.isWithUpdateFill()) {
                handler.updateFill(metaObject);
            }
        }
    }

    private static MetaObjectHandler getMetaObjectHandler(Configuration configuration) {
        GlobalConfig globalConfig = GlobalConfigUtils.getGlobalConfig(configuration);
        return globalConfig == null ? null : globalConfig.getMetaObjectHandler();
    }

    /**
     * 从参数对象中提取 Upsert 实体。
     *
     * <p>单行 Upsert（以及通过 {@code MybatisBatch} 执行的每个 {@code upsertExecutor} 行）
     * 将实体放在 {@code et} 键下；SQL 批量 Upsert 将集合放在 {@code list} 键下。
     * 使用 {@code containsKey} 是因为 {@code ParamMap.get()} 对不存在的键会抛出
     * {@code BindingException}。
     */
    @SuppressWarnings("unchecked")
    private static List<Object> extractEntities(Object parameter) {
        if (parameter instanceof Map) {
            Map<String, Object> paramMap = (Map<String, Object>) parameter;
            List<Object> entities = new ArrayList<>();

            if (paramMap.containsKey("et")) {
                entities.add(paramMap.get("et"));
            }
            if (paramMap.containsKey("list")) {
                Object list = paramMap.get("list");
                if (list instanceof Collection) {
                    entities.addAll((Collection<?>) list);
                }
            }
            return entities;
        }
        return Collections.singletonList(parameter);
    }
}
