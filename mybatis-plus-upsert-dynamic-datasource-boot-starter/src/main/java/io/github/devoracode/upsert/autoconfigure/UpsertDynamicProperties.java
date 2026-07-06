package io.github.devoracode.upsert.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "mybatis-plus.upsert.dynamic")
public class UpsertDynamicProperties {

    private boolean enabled = true;
    private Map<String, DataSourceConfig> datasource = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, DataSourceConfig> getDatasource() {
        return datasource;
    }

    public void setDatasource(Map<String, DataSourceConfig> datasource) {
        this.datasource = datasource;
    }

    public static class DataSourceConfig {
        private String dbType;
        private boolean useNewMysqlSyntax = false;
        /**
         * Bean name of a user-defined {@code UpsertDialect} to use when
         * {@code db-type} is set to {@code custom}. Ignored for built-in db types.
         */
        private String dialectRef;

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

        public String getDialectRef() {
            return dialectRef;
        }

        public void setDialectRef(String dialectRef) {
            this.dialectRef = dialectRef;
        }
    }
}
