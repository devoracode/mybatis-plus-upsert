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
 * @author devoracode
 * @since 1.6.0
 */
public final class UpsertFillProcessor {

    private UpsertFillProcessor() {
    }

    /**
     * 按策略填充参数对象里的实体。
     *
     * @param strategy {@code null} 等同于 {@link FillStrategy#NONE}，即不做任何填充
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

    private static List<Object> extractEntities(Object parameter) {
        if (parameter instanceof Map) {
            Map<?, ?> paramMap = (Map<?, ?>) parameter;
            Object entity = paramMap.getOrDefault("et", null);
            return entity == null ? Collections.emptyList() : Collections.singletonList(entity);
        }
        return Collections.singletonList(parameter);
    }
}
