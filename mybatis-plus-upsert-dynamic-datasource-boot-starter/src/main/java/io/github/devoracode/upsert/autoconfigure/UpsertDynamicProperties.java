package io.github.devoracode.upsert.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "mybatis-plus.upsert.dynamic")
public class UpsertDynamicProperties {

    private boolean enabled = true;
    private Map<String, DataSourceConfig> datasources = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, DataSourceConfig> getDatasources() {
        return datasources;
    }

    public void setDatasources(Map<String, DataSourceConfig> datasources) {
        this.datasources = datasources;
    }

    public static class DataSourceConfig {
        private String dbType;
        private boolean useNewMysqlSyntax = false;

        public String getDbType() {
            return dbType;
        }

        public void setDbType(String dbType) {
            this.dbType = dbType;
        }

        public boolean isUseNewMysqlSyntax() {
            return useNewMysqlSyntax;
        }

        public void setUseNewMysqlSyntax(boolean useNewMysqlSyntax) {
            this.useNewMysqlSyntax = useNewMysqlSyntax;
        }
    }
}
