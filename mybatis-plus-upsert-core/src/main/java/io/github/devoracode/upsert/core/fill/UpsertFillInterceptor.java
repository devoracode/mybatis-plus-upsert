package io.github.devoracode.upsert.core.fill;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import io.github.devoracode.upsert.core.UpsertMethodNames;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * MyBatis {@link org.apache.ibatis.plugin.Interceptor} that supplements
 * {@code updateFill} for upsert operations.
 *
 * <p>MyBatis-Plus' native {@code MybatisParameterHandler} triggers
 * {@code insertFill} for {@code SqlCommandType.INSERT} operations, but only
 * after {@code SqlSource.getBoundSql()} has resolved the dynamic SQL columns.
 * If a fill-annotated field (e.g. {@code FieldFill.INSERT}) is null at SQL
 * binding time and its {@code insertStrategy} is {@code NOT_NULL}, the column
 * is omitted from the SQL entirely — even if {@code insertFill} later sets a
 * value.
 *
 * <p>To ensure fill-annotated fields are included in the generated SQL, this
 * interceptor invokes <em>both</em> {@code insertFill} and {@code updateFill}
 * at the {@code Executor.update} stage, <em>before</em> {@code getBoundSql()} is called. The native {@code MybatisParameterHandler} will subsequently call
 * {@code insertFill} again, but {@code strictInsertFill} skips fields that
 * already have a value, so the duplicate call is harmless.
 *
 * <p>This approach mirrors MyBatis-Plus' native fill mechanism:
 * <ul>
 *   <li>{@code MetaObjectHandler} is looked up from {@code GlobalConfig} at
 *       runtime (not passed through a constructor chain)</li>
 *   <li>{@code TableInfo.isWithInsertFill()} / {@code isWithUpdateFill()} are
 *       checked to skip entities that have no fill-annotated fields</li>
 * </ul>
 *
 * <p>The interceptor is only active when {@code mybatis-plus.upsert.auto-fill}
 * is {@code true} (the default). When disabled, the interceptor bean is not
 * registered, and only MP's native {@code insertFill} runs for upsert operations.
 *
 * @author devoracode
 * @since 1.5.1
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class UpsertFillInterceptor implements org.apache.ibatis.plugin.Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        if (isUpsertMethod(ms.getId())) {
            applyFill(parameter, ms.getConfiguration());
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return target instanceof Executor ? Plugin.wrap(target, this) : target;
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }

    private boolean isUpsertMethod(String mappedStatementId) {
        for (String methodName : UpsertMethodNames.ALL) {
            if (mappedStatementId.endsWith("." + methodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Invokes both {@code insertFill} and {@code updateFill} on each upsert entity
     * before {@code getBoundSql()} resolves the dynamic SQL columns.
     *
     * <p>{@code insertFill} runs first so that {@code FieldFill.INSERT} fields
     * (e.g. {@code createTime}) have non-null values when the SQL is bound,
     * ensuring columns with {@code NOT_NULL} strategy are included in the
     * generated SQL. {@code updateFill} runs next for {@code FieldFill.UPDATE}
     * and {@code FieldFill.INSERT_UPDATE} fields (e.g. {@code updateTime}).
     *
     * <p>The native {@code MybatisParameterHandler} will call {@code insertFill}
     * again later, but {@code strictInsertFill} skips fields that already have
     * a value, so the duplicate call is harmless.
     */
    private void applyFill(Object parameter, Configuration configuration) {
        GlobalConfig globalConfig = GlobalConfigUtils.getGlobalConfig(configuration);
        MetaObjectHandler handler = globalConfig.getMetaObjectHandler();
        if (handler == null) {
            return;
        }

        for (Object entity : extractEntities(parameter)) {
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
            if (tableInfo.isWithUpdateFill()) {
                handler.updateFill(metaObject);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractEntities(Object parameter) {
        if (parameter == null) {
            return Collections.emptyList();
        }
        if (parameter instanceof Map) {
            Map<String, Object> paramMap = (Map<String, Object>) parameter;
            List<Object> entities = new ArrayList<>();

            // Single-row upsert: entity is under "et" key
            // Use containsKey because MyBatis ParamMap.get() throws BindingException for missing keys
            if (paramMap.containsKey("et")) {
                Object et = paramMap.get("et");
                if (et != null) {
                    entities.add(et);
                }
            }

            // Batch upsert: collection is under "list" key
            if (paramMap.containsKey("list")) {
                Object list = paramMap.get("list");
                if (list instanceof Collection) {
                    for (Object item : (Collection<?>) list) {
                        entities.add(item);
                    }
                }
            }

            return entities;
        }
        // Direct entity object
        return Collections.singletonList(parameter);
    }
}
