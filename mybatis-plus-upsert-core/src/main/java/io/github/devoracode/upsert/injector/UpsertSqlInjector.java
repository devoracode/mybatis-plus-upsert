package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.session.Configuration;

import java.util.List;

public class UpsertSqlInjector extends DefaultSqlInjector {

    private final UpsertDialect dialect;

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
