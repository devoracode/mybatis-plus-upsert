package io.github.devoracode.upsert.autoconfigure;

import io.github.devoracode.upsert.util.DbTypeDetector;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Spring {@link Condition} that matches when the configured db-type is NOT {@code custom}.
 *
 * <p>Used to guard the auto-configured {@link io.github.devoracode.upsert.dialect.UpsertDialect}
 * bean so it is only created when the user has not opted into a user-provided custom dialect
 * (i.e. {@code mybatis-plus.upsert.db-type=custom}).
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
