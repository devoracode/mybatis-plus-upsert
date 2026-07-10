package io.github.devoracode.upsert.core.fill;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * Default {@link UpsertFieldFillHandler} that bridges to MyBatis-Plus'
 * {@link MetaObjectHandler}, invoking both {@code insertFill} and
 * {@code updateFill} because upsert is semantically "insert or update".
 *
 * <p>Both fills are invoked before SQL generation so that null fields
 * (e.g. {@code createTime}) are populated and included in the INSERT clause.
 * Note that {@code insertFill} will also be called by MyBatis-Plus' native
 * {@code MybatisParameterHandler} since upsert uses INSERT command type,
 * but this is harmless because MP's {@code strictInsertFill}/
 * {@code strictUpdateFill} methods skip fields that already have values.
 *
 * <p>The {@link Configuration} is supplied by the caller at execution time,
 * so this handler is stateless and has no dependency on Spring or on the
 * order in which the MyBatis configuration is created.
 *
 * @author devoracode
 * @since 1.5.0
 */
public class MetaObjectHandlerFillHandler implements UpsertFieldFillHandler {

    @Override
    public void fillBeforeUpsert(Object entity, Configuration configuration) {
        if (entity == null || configuration == null) {
            return;
        }
        GlobalConfig globalConfig = GlobalConfigUtils.getGlobalConfig(configuration);
        MetaObjectHandler handler = globalConfig.getMetaObjectHandler();
        if (handler == null) {
            return;
        }
        MetaObject metaObject = configuration.newMetaObject(entity);
        handler.insertFill(metaObject);
        handler.updateFill(metaObject);
    }
}
