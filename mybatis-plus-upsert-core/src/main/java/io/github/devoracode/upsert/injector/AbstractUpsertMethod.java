package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlInjectionUtils;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.UpsertMetaParser;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * Upsert 注入方法的基类：子类只声明要注册的语句名，公共的元数据解析与
 * {@link SqlSource} 构建由本类完成（{@link UpsertSqlSourceFactory}）。
 *
 * <p>注入的语句一律是单行 SQL：批量写入由 MP 的 {@code MybatisBatch} 在
 * {@code ExecutorType.BATCH} 下把同一份语句逐条提交，不另生成多行 SQL。
 * 实体没有 {@link io.github.devoracode.upsert.annotation.ConflictKey} 字段时不注入任何语句
 * （返回 {@code null}），该 Mapper 对此实体不启用 Upsert。
 *
 * @author devoracode
 * @since 1.0.0
 */
abstract class AbstractUpsertMethod extends AbstractMethod {

    final UpsertDialect dialect;
    private final FillStrategy fillStrategy;

    AbstractUpsertMethod(String methodName, UpsertDialect dialect) {
        this(methodName, dialect, FillStrategy.INSERT_UPDATE);
    }

    /**
     * @param fillStrategy SQL 绑定前应用的自动填充策略
     */
    AbstractUpsertMethod(String methodName, UpsertDialect dialect, FillStrategy fillStrategy) {
        super(methodName);
        this.dialect = dialect;
        this.fillStrategy = fillStrategy;
    }

    /**
     * 主键生成器与 MP 原生 {@code Insert} 走同一套机制，单条与 {@code upsertExecutor}
     * 两条语句都配置：{@code IdType.AUTO} 用 {@link Jdbc3KeyGenerator}（值取自数据库返回的
     * generated keys，不是内存预测值），序列主键复用 {@link TableInfoHelper#genKeyGenerator}
     * 并由 {@link SequenceKeyGeneratorDecorator} 把号搬回实体，其余策略不回填。
     * 批量路径的生成键在 {@code flushStatements} 时写回。
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        if (!UpsertMetaParser.hasConflictKey(modelClass)) {
            return null;
        }
        // 只用 MP 注入期直接递入的这份 TableInfo（它属于当前 Configuration）：解析器无状态，
        // 多个 Configuration/ApplicationContext 共存时结构上不存在串用元数据的通道
        UpsertMeta meta = UpsertMetaParser.getMeta(tableInfo);
        SqlSource sqlSource = UpsertSqlSourceFactory.create(
                configuration, languageDriver, meta, dialect, modelClass, fillStrategy);

        KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
        String keyProperty = null;
        String keyColumn = null;
        // 与 MP 原生 Insert 一致：实体含主键时按 IdType 配置回填；
        // 语句是单行 SQL，generated keys 与行的对应关系明确
        if (StringUtils.isNotBlank(tableInfo.getKeyProperty())) {
            if (tableInfo.getIdType() == IdType.AUTO) {
                keyGenerator = Jdbc3KeyGenerator.INSTANCE;
                keyProperty = tableInfo.getKeyProperty();
                keyColumn = SqlInjectionUtils.removeEscapeCharacter(tableInfo.getKeyColumn());
            } else if (null != tableInfo.getKeySequence()) {
                /* 序列主键：取号仍由 MP 完成，只补一步把号写回 @Param("et") 包裹的实体 */
                keyGenerator = new SequenceKeyGeneratorDecorator(
                        TableInfoHelper.genKeyGenerator(methodName, tableInfo, builderAssistant),
                        configuration, tableInfo.getKeyProperty());
                keyProperty = tableInfo.getKeyProperty();
                keyColumn = tableInfo.getKeyColumn();
            }
        }
        return this.addInsertMappedStatement(
                mapperClass, modelClass, methodName, sqlSource, keyGenerator, keyProperty, keyColumn);
    }
}
