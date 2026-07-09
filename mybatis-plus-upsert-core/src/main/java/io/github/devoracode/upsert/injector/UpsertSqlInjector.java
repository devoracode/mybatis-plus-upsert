package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
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

    /**
     * Creates a new UpsertSqlInjector with the specified dialect.
     *
     * @param dialect the upsert dialect to use for SQL generation (must not be null)
     */
    public UpsertSqlInjector(UpsertDialect dialect) {
        this.dialect = dialect;
    }

    @Override
    public List<AbstractMethod> getMethodList(Configuration configuration, Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> methods = super.getMethodList(configuration, mapperClass, tableInfo);
        methods.add(new UpsertMethod(dialect));
        methods.add(new UpsertBatchMethod(dialect));
        methods.add(new UpsertExecutorMethod(dialect));
        return methods;
    }
}