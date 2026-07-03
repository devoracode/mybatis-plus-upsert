package io.github.devoracode.upsert.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "mybatis-plus.upsert")
public class UpsertProperties {

    private boolean enabled = true;

    private String dbType;

    private boolean useNewMysqlSyntax = false;
}
