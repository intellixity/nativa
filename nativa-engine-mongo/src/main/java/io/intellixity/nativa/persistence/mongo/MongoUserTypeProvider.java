package io.intellixity.nativa.persistence.mongo;

import org.bson.Document;
import io.intellixity.nativa.persistence.authoring.UserType;
import io.intellixity.nativa.persistence.authoring.UserTypeProvider;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Mongo-specific UserTypes (dialectId="mongo").
 *
 * Key override: logical {@code json} should be stored/queryable as a Document/Map (not JSON text),
 * otherwise JSON operators cannot work.
 */
public final class MongoUserTypeProvider implements UserTypeProvider {
  @Override
  public String dialectId() {
    return "mongo";
  }

  @Override
  public Collection<UserType<?>> userTypes() {
    return List.of(new MongoJsonUserType());
  }

  /** For Mongo, keep json as native map/document-like values. */
  static final class MongoJsonUserType implements UserType<Object> {
    @Override public String id() { return "json"; }
    @Override public Class<Object> javaType() { return Object.class; }

    @Override
    public Object decode(Object raw) {
      if (raw == null) return null;
      if (raw instanceof Document d) return d;
      if (raw instanceof Map<?, ?>) return raw;
      if (raw instanceof List<?>) return raw;
      // If legacy data is stored as String, leave it as-is (querying inside it won't work).
      return raw;
    }

    @Override
    public Object encode(Object value) {
      if (value == null) return null;
      if (value instanceof Document) return value;
      if (value instanceof Map<?, ?>) return value;
      if (value instanceof List<?>) return value;
      // For scalars, keep scalar.
      return value;
    }
  }
}




