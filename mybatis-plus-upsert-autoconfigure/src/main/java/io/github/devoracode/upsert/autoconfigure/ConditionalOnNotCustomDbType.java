package io.github.devoracode.upsert.autoconfigure;

import io.github.devoracode.upsert.util.DbTypeDetector;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class ConditionalOnNotCustomDbType implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String dbType = context.getEnvironment().getProperty("mybatis-plus.upsert.db-type");
        return !"custom".equalsIgnoreCase(dbType);
    }
}
