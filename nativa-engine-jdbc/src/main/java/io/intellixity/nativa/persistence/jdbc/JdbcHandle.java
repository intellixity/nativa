package io.intellixity.nativa.persistence.jdbc;

import io.intellixity.nativa.persistence.exec.handle.EngineHandle;

import javax.sql.DataSource;
import java.util.Objects;

/** JDBC-family engine handle (resolved by application code). */
public final class JdbcHandle implements EngineHandle<DataSource> {
  private final String id;
  private final DataSource client;
  private final String schema;
  private final boolean multiTenant;

  public JdbcHandle(String id, DataSource client, String schema, boolean multiTenant) {
    this.id = Objects.requireNonNull(id, "id");
    this.client = Objects.requireNonNull(client, "client");
    this.schema = (schema == null || schema.isBlank()) ? null : schema;
    this.multiTenant = multiTenant;
  }

  @Override public String id() { return id; }
  @Override public DataSource client() { return client; }
  @Override public String namespace() { return schema; }
  @Override public boolean multiTenant() { return multiTenant; }

  public String schema() { return schema; }
}




