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
 * 所有 Upsert SQL 注入方法的基类。
 *
 * <p>子类只需声明方法名和是否使用批量 SQL；本基类处理解析 Upsert 元数据
 * 并通过 {@link UpsertSqlSourceFactory} 构建 {@link SqlSource} 的公共逻辑。
 *
 * <p>如果实体类没有 {@link io.github.devoracode.upsert.annotation.ConflictKey} 字段，
 * 则不会注入任何语句（返回 {@code null}），该 Mapper 方法对此实体不生效。
 *
 * <p><strong>主键回填</strong>：按 MyBatis-Plus 原生 {@code Insert} 的标准机制配置
 * {@link KeyGenerator}（AUTO 使用 {@link Jdbc3KeyGenerator}，序列主键复用
 * {@link TableInfoHelper#genKeyGenerator}），但仅对单行 SQL 路径承诺回填，
 * 且回填值取自数据库返回的生成键而非内存预测值，详见 {@link #injectMappedStatement}。
 *
 * <p>注入的 {@link SqlSource} 由 {@link ParameterGuardSqlSource} 包装，
 * {@code null} 实体与含 {@code null} 元素的集合在 SQL 绑定之前即被拒绝。
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
     * @param batch      此方法是否使用批量（多行 VALUES）Upsert SQL
     */
    AbstractUpsertMethod(String methodName, UpsertDialect dialect, boolean batch) {
        this(methodName, dialect, batch, FillStrategy.INSERT_UPDATE);
    }

    /**
     * 创建新的 Upsert 注入方法。
     *
     * @param methodName   要注册的 Mapper 方法名（如 "upsert"、"upsertBatch"）
     * @param dialect      用于构建 Upsert SQL 的方言
     * @param batch        此方法是否使用批量（多行 VALUES）Upsert SQL
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
     * <p>主键回填按方法类型区分三条路径：
     * <ul>
     *   <li>{@code upsert}（单条）：与 MyBatis-Plus 原生 {@code Insert} 完全一致——
     *       {@code IdType.AUTO} 配置 {@link Jdbc3KeyGenerator} 及 keyProperty/keyColumn，
     *       序列主键复用 {@link TableInfoHelper#genKeyGenerator}（外面套一层
     *       {@link SequenceKeyGeneratorDecorator} 负责把号写回实体），其余策略不回填；</li>
     *   <li>{@code upsert(Collection)}（内部经 {@code upsertExecutor} 语句在
     *       {@code ExecutorType.BATCH} 下逐条提交单行 SQL）：同样配置上述生成器，
     *       生成键在 {@code flushStatements} 时回填；</li>
     *   <li>{@code upsertBatch}（单条多行 VALUES SQL）：保持 {@link NoKeyGenerator}，
     *       <strong>不承诺</strong>生成主键回填——多行语句的 generated keys 数量与行的
     *       对应关系受数据库与 JDBC 驱动差异影响（MySQL 下冲突更新行的返回值不固定，
     *       PostgreSQL 冲突更新路径不返回 RETURNING 行），强行配置会导致键数校验异常。</li>
     * </ul>
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
        // 解析只使用 MP 注入期直接递入的这份 TableInfo（它属于当前 Configuration），
        // 解析器本身无状态，多个 Configuration/ApplicationContext 共存时
        // 结构上不存在串用其他上下文元数据的通道
        UpsertMeta meta = UpsertMetaParser.getMeta(tableInfo);
        SqlSource sqlSource = UpsertSqlSourceFactory.create(
                configuration, languageDriver, meta, dialect, batch, modelClass, fillStrategy);

        KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
        String keyProperty = null;
        String keyColumn = null;
        // 与 MP 原生 Insert 一致：仅单行 SQL 路径且实体含主键时配置回填；
        // 多行 upsertBatch（batch=true）的生成键与行的对应关系不可靠，明确不承诺回填
        if (!batch && StringUtils.isNotBlank(tableInfo.getKeyProperty())) {
            if (tableInfo.getIdType() == IdType.AUTO) {
                /* 自增主键 */
                keyGenerator = Jdbc3KeyGenerator.INSTANCE;
                keyProperty = tableInfo.getKeyProperty();
                // 去除转义符
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
