package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.UpsertMetaParser;
import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

public class UpsertExecutorMethod extends AbstractMethod {

    public static final String METHOD_NAME = UpsertMethodNames.UPSERT_EXECUTOR;

    private final UpsertDialect dialect;

    public UpsertExecutorMethod(UpsertDialect dialect) {
        super(METHOD_NAME);
        this.dialect = dialect;
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        if (!UpsertMethod.hasConflictKey(modelClass)) {
            return null;
        }
        UpsertMeta meta = UpsertMetaParser.getMeta(modelClass);
        SqlSource sqlSource = UpsertSqlSourceFactory.create(
                configuration, languageDriver, meta, dialect, false, modelClass);
        return this.addInsertMappedStatement(
                mapperClass, modelClass, METHOD_NAME, sqlSource,
                new NoKeyGenerator(), null, null);
    }
}
