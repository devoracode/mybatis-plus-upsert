package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.UpsertMetaParser;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * Base class for all upsert SQL injection methods.
 * Subclasses just declare the method name and whether batch SQL is needed.
 */
abstract class AbstractUpsertMethod extends AbstractMethod {

    final UpsertDialect dialect;
    private final boolean batch;
    private final String methodName;

    AbstractUpsertMethod(String methodName, UpsertDialect dialect, boolean batch) {
        super(methodName);
        this.methodName = methodName;
        this.dialect = dialect;
        this.batch = batch;
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        if (!UpsertMetaParser.hasConflictKey(modelClass)) {
            return null;
        }
        UpsertMeta meta = UpsertMetaParser.getMeta(modelClass);
        SqlSource sqlSource = UpsertSqlSourceFactory.create(
                configuration, languageDriver, meta, dialect, batch, modelClass);
        return this.addInsertMappedStatement(
                mapperClass, modelClass, methodName, sqlSource,
                new NoKeyGenerator(), null, null);
    }
}
