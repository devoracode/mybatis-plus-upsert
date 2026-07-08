package io.github.devoracode.upsert.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "mybatis-plus.upsert.dynamic")
public class UpsertDynamicProperties {

    private boolean enabled = true;
    private boolean useNewMysqlSyntax = false;
    private Map<String, DataSourceConfig> datasource = new HashMap<>();

    @Data
    public static class DataSourceConfig {
        private String dbType;
        private boolean useNewMysqlSyntax = false;
        private String dialectRef;
    }
}
