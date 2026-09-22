package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.session.Configuration;

import java.util.List;

/**
 * 在 MyBatis-Plus {@link DefaultSqlInjector} 的基础上追加 {@link UpsertMethod} 与
 * {@link UpsertExecutorMethod} 两条注入语句的 SQL 注入器。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertSqlInjector extends DefaultSqlInjector {

    private final UpsertDialect dialect;
    private final FillStrategy fillStrategy;

    /**
     * 使用默认填充策略 {@link FillStrategy#INSERT_UPDATE}。
     *
     * @param dialect 用于 SQL 生成的 Upsert 方言（不能为 null）
     */
    public UpsertSqlInjector(UpsertDialect dialect) {
        this(dialect, FillStrategy.INSERT_UPDATE);
    }

    /**
     * @param fillStrategy SQL 绑定前应用的自动填充策略
     * @since 1.6.0
     */
    public UpsertSqlInjector(UpsertDialect dialect, FillStrategy fillStrategy) {
        this.dialect = dialect;
        this.fillStrategy = fillStrategy;
    }

    @Override
    public List<AbstractMethod> getMethodList(Configuration configuration, Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> methods = super.getMethodList(configuration, mapperClass, tableInfo);
        methods.add(new UpsertMethod(dialect, fillStrategy));
        methods.add(new UpsertExecutorMethod(dialect, fillStrategy));
        return methods;
    }
}
