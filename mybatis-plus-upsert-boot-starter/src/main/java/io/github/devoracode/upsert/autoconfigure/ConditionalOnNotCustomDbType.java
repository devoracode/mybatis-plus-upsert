package io.github.devoracode.upsert.autoconfigure;

import io.github.devoracode.upsert.util.DbTypeDetector;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Spring {@link Condition} 条件匹配器，当配置的 db-type 不是 {@code custom} 时匹配成功。
 *
 * <p>用于保护自动配置的 {@link io.github.devoracode.upsert.dialect.UpsertDialect}
 * Bean，使其仅在用户未选择自定义方言时使用（即 {@code mybatis-plus.upsert.db-type=custom}）。
 *
 * @author devoracode
 * @since 1.0.0
 */
class ConditionalOnNotCustomDbType implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String dbType = context.getEnvironment().getProperty("mybatis-plus.upsert.db-type");
        return !DbTypeDetector.DbType.CUSTOM.name().equalsIgnoreCase(dbType);
    }
}
