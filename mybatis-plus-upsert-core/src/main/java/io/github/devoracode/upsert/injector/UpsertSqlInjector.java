package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import io.github.devoracode.upsert.core.fill.UpsertFieldFillHandler;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.session.Configuration;

import java.util.List;

/**
 * Custom SQL injector that registers upsert methods into MyBatis-Plus.
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertSqlInjector extends DefaultSqlInjector {

    private final UpsertDialect dialect;
    private final UpsertFieldFillHandler fillHandler;

    /**
     * Creates a new UpsertSqlInjector with the specified dialect and fill handler.
     *
     * @param dialect     the upsert dialect to use for SQL generation (must not be null)
     * @param fillHandler the field fill handler for auto-filling (must not be null)
     */
    public UpsertSqlInjector(UpsertDialect dialect, UpsertFieldFillHandler fillHandler) {
        this.dialect = dialect;
        this.fillHandler = fillHandler;
    }

    @Override
    public List<AbstractMethod> getMethodList(Configuration configuration, Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> methods = super.getMethodList(configuration, mapperClass, tableInfo);
        methods.add(new UpsertMethod(dialect, fillHandler));
        methods.add(new UpsertBatchMethod(dialect, fillHandler));
        methods.add(new UpsertExecutorMethod(dialect, fillHandler));
        return methods;
    }
}
