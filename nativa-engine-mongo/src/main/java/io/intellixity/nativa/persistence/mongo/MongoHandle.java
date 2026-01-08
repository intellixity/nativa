package io.intellixity.nativa.persistence.mongo;

import com.mongodb.client.MongoClient;
import io.intellixity.nativa.persistence.exec.handle.EngineHandle;

import java.util.Objects;

/** Mongo engine handle (resolved by application code). */
public final class MongoHandle implements EngineHandle<MongoClient> {
  private final String id;
  private final MongoClient client;
  private final String database;
  private final boolean multiTenant;

  public MongoHandle(String id, MongoClient client, String database, boolean multiTenant) {
    this.id = Objects.requireNonNull(id, "id");
    this.client = Objects.requireNonNull(client, "client");
    this.database = Objects.requireNonNull(database, "database");
    this.multiTenant = multiTenant;
  }

  @Override public String id() { return id; }
  @Override public MongoClient client() { return client; }
  @Override public String namespace() { return database; }
  @Override public boolean multiTenant() { return multiTenant; }

  public String database() { return database; }
}

