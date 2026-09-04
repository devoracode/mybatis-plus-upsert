package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.UpsertMetaParser;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 所有 Upsert SQL 注入方法的基类。
 *
 * <p>子类只需声明方法名和是否使用批量 SQL；本基类处理解析 Upsert 元数据
 * 并通过 {@link UpsertSqlSourceFactory} 构建 {@link SqlSource} 的公共逻辑。
 *
 * <p>如果实体类没有 {@link io.github.devoracode.upsert.annotation.ConflictKey} 字段，
 * 则不会注入任何语句（返回 {@code null}），该 Mapper 方法对此实体不生效。
 *
 * @author devoracode
 * @since 1.0.0
 */
abstract class AbstractUpsertMethod extends AbstractMethod {

    final UpsertDialect dialect;
    private final boolean batch;
    private final FillStrategy fillStrategy;

    /**
     * 使用默认填充策略（{@link FillStrategy#INSERT_UPDATE}）创建新的 Upsert 注入方法。
     *
     * @param methodName 要注册的 Mapper 方法名（如 "upsert"、"upsertBatch"）
     * @param dialect    用于构建 Upsert SQL 的方言
     * @param batch      此方法是否使用批量 Upsert SQL
     */
    AbstractUpsertMethod(String methodName, UpsertDialect dialect, boolean batch) {
        this(methodName, dialect, batch, FillStrategy.INSERT_UPDATE);
    }

    /**
     * 创建新的 Upsert 注入方法。
     *
     * @param methodName   要注册的 Mapper 方法名（如 "upsert"、"upsertBatch"）
     * @param dialect      用于构建 Upsert SQL 的方言
     * @param batch        此方法是否使用批量 Upsert SQL
     * @param fillStrategy SQL 绑定前应用的自动填充策略
     * @since 1.6.0
     */
    AbstractUpsertMethod(String methodName, UpsertDialect dialect, boolean batch, FillStrategy fillStrategy) {
        super(methodName);
        this.dialect = dialect;
        this.batch = batch;
        this.fillStrategy = fillStrategy;
    }

    /**
     * 为给定实体注入 Upsert {@link MappedStatement}。
     *
     * <p>当实体没有 {@code @ConflictKey} 字段时返回 {@code null}，导致该 Mapper 跳过 Upsert 方法。
     *
     * @param mapperClass Mapper 接口类
     * @param modelClass  实体类
     * @param tableInfo   MyBatis-Plus 表元数据
     * @return 注入的 MappedStatement，若无冲突键则返回 null
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        if (!UpsertMetaParser.hasConflictKey(modelClass)) {
            return null;
        }
        UpsertMeta meta = UpsertMetaParser.getMeta(modelClass);
        SqlSource sqlSource = UpsertSqlSourceFactory.create(
                configuration, languageDriver, meta, dialect, batch, modelClass, fillStrategy);
        return this.addInsertMappedStatement(
                mapperClass, modelClass, methodName, sqlSource,
                new NoKeyGenerator(), null, null);
    }
}
