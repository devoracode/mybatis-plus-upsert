package io.github.devoracode.upsert.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mybatis-plus.upsert")
public class UpsertProperties {

    private boolean enabled = true;
    private String dbType;
    private boolean useNewMysqlSyntax = false;
}
