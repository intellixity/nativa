package io.intellixity.nativa.examples.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "tenants")
public class TenantsProperties {
  private final Map<String, TenantDb> db = new HashMap<>();

  public Map<String, TenantDb> getDb() { return db; }

  public static class TenantDb {
    private String jdbcUrl;
    private String username;
    private String password;
    private String schema = "public";

    /** Optional read-only JDBC URL; if absent, jdbcUrl will be used. */
    private String readOnlyJdbcUrl;

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getReadOnlyJdbcUrl() { return readOnlyJdbcUrl; }
    public void setReadOnlyJdbcUrl(String readOnlyJdbcUrl) { this.readOnlyJdbcUrl = readOnlyJdbcUrl; }
  }
}




