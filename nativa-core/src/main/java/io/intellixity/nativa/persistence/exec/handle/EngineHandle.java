package io.intellixity.nativa.persistence.exec.handle;

/**
 * Resolved runtime handle for a backend engine family.\n
 *
 * Example:\n
 * - JDBC: client() is javax.sql.DataSource, namespace() is schema\n
 * - Mongo: client() is MongoClient, namespace() is database\n
 */
public interface EngineHandle<TClient> {
  /** Unique identifier for this handle (useful for logging/caching). */
  String id();

  /** Native client/handle used by an engine (DataSource, MongoClient, etc.). */
  TClient client();

  /** Namespace (schema/database) for this handle. */
  String namespace();

  /** True if this backend store contains multiple tenants in the same physical store. */
  boolean multiTenant();
}




