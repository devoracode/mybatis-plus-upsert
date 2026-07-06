package io.github.devoracode.upsert.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mybatis-plus.upsert")
public class UpsertProperties {

    private boolean enabled = true;
    private String dbType;
    private boolean useNewMysqlSyntax = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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
