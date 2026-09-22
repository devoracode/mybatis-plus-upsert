package io.github.devoracode.upsert.autoconfigure;

import io.github.devoracode.upsert.util.DbTypeDetector;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 当 {@code mybatis-plus.upsert.db-type} 不是 {@code custom} 时匹配的 {@link Condition}：
 * {@code custom} 表示用户自带方言 Bean，此时不注册内置 {@link io.github.devoracode.upsert.dialect.UpsertDialect}。
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
