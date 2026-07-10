package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.UpsertMetaParser;
import io.github.devoracode.upsert.core.fill.UpsertFieldFillHandler;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * Base class for all upsert SQL injection methods.
 *
 * <p>Subclasses only need to declare the method name and whether batch SQL is needed;
 * this base class handles the common logic of parsing upsert metadata and building the
 * {@link SqlSource} via {@link UpsertSqlSourceFactory}.
 *
 * <p>If the entity class has no {@link io.github.devoracode.upsert.annotation.ConflictKey}
 * field, no statement is injected (returns {@code null}), so the mapper method simply
 * does not exist for that entity.
 *
 * @author devoracode
 * @since 1.0.0
 */
abstract class AbstractUpsertMethod extends AbstractMethod {

    final UpsertDialect dialect;
    private final boolean batch;
    private final String methodName;
    final UpsertFieldFillHandler fillHandler;

    /**
     * Creates a new upsert injection method.
     *
     * @param methodName  the mapper method name to register (e.g. "upsert", "upsertBatch")
     * @param dialect     the dialect used to build the upsert SQL
     * @param batch       whether this method uses batch upsert SQL
     * @param fillHandler the field fill handler for auto-filling
     */
    AbstractUpsertMethod(String methodName, UpsertDialect dialect, boolean batch, UpsertFieldFillHandler fillHandler) {
        super(methodName);
        this.methodName = methodName;
        this.dialect = dialect;
        this.batch = batch;
        this.fillHandler = fillHandler;
    }

    /**
     * Injects the upsert {@link MappedStatement} for the given entity.
     *
     * <p>Returns {@code null} when the entity has no {@code @ConflictKey} field, causing
     * the upsert method to be skipped for that mapper.
     *
     * @param mapperClass the mapper interface class
     * @param modelClass  the entity class
     * @param tableInfo   the MyBatis-Plus table metadata
     * @return the injected MappedStatement, or null if no conflict key is present
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        if (!UpsertMetaParser.hasConflictKey(modelClass)) {
            return null;
        }
        UpsertMeta meta = UpsertMetaParser.getMeta(modelClass);
        SqlSource sqlSource = UpsertSqlSourceFactory.create(
                configuration, languageDriver, meta, dialect, batch, modelClass, fillHandler);
        return this.addInsertMappedStatement(
                mapperClass, modelClass, methodName, sqlSource,
                new NoKeyGenerator(), null, null);
    }
}
