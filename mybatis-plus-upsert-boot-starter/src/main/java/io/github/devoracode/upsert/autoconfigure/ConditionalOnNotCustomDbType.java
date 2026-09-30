package io.github.devoracode.upsert.autoconfigure;

import io.github.devoracode.upsert.util.DbTypeDetector;
import org.springframework.boot.context.properties.bind.Binder;
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
        // 必须走 Binder：Environment.getProperty 是精确键查找，dbType 这类驼峰写法会漏掉，
        // 与 @ConfigurationProperties 的宽松绑定不一致——条件按"非 custom"放行，内置方言
        // Bean 随后拿到 custom 建不出来
        String dbType = Binder.get(context.getEnvironment())
                .bind("mybatis-plus.upsert.db-type", String.class)
                .orElse(null);
        return !DbTypeDetector.DbType.CUSTOM.name().equalsIgnoreCase(dbType);
    }
}
