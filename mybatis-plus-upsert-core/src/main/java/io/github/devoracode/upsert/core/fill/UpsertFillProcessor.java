package io.github.devoracode.upsert.core.fill;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 在动态 Upsert SQL 绑定之前执行 MyBatis-Plus 自动填充的无状态执行器。
 *
 * <p><b>为什么要在绑定前填充</b>：{@code BaseStatementHandler} 先调用
 * {@code SqlSource.getBoundSql()} 才创建 {@code ParameterHandler}，而 MP 的原生填充在
 * {@code MybatisParameterHandler} 内部进行。{@code <if test="et.x != null">} 在
 * {@code getBoundSql} 阶段求值，于是绑定时仍为 null 的填充字段会被裁出 SQL——
 * 本执行器先于该阶段运行，是 Upsert 语句的权威填充点。
 *
 * <p><b>可能被调用两次</b>：注入的语句注册为 {@code SqlCommandType.INSERT}，MP 原生
 * {@code MybatisParameterHandler} 稍后还会再触发一次 {@code insertFill}；
 * {@code updateFill} 则只由本执行器触发（原生路径对 INSERT 命令从不调用它）。
 * 使用 {@code strictInsertFill}/{@code strictUpdateFill} 时第二次是空操作。
 * {@code upsert(Collection)} 逐条提交，因此每个实体各自经历上述流程。
 *
 * @author devoracode
 * @since 1.6.0
 */
public final class UpsertFillProcessor {

    private UpsertFillProcessor() {
    }

    /**
     * 按策略填充参数对象里的实体。
     *
     * @param strategy {@code null} 等同于 {@link FillStrategy#NONE}，即什么都不做
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
     * 取参数对象里待填充的实体：单行语句（含 {@code upsertExecutor} 的每一行）把实体放在
     * {@code et} 键下，裸实体参数则直接取自身。用 {@code containsKey} 而非 {@code get}，
     * 因为 {@code ParamMap.get()} 对缺失键抛 {@code BindingException}。
     */
    private static List<Object> extractEntities(Object parameter) {
        if (parameter instanceof Map) {
            Map<?, ?> paramMap = (Map<?, ?>) parameter;
            return paramMap.containsKey("et")
                    ? Collections.singletonList(paramMap.get("et"))
                    : Collections.emptyList();
        }
        return Collections.singletonList(parameter);
    }
}
