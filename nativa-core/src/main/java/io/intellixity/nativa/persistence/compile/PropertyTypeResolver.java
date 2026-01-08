package io.intellixity.nativa.persistence.compile;

import io.intellixity.nativa.persistence.authoring.*;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a filter property path (dot path) to a scalar {@code userTypeId} using authoring.
 * <p>
 * This is used by dialect renderers when rendering Query-only filters.
 */
public final class PropertyTypeResolver {
  private final AuthoringRegistry authoring;
  private final Map<CacheKey, String> cache = new ConcurrentHashMap<>();

  public PropertyTypeResolver(AuthoringRegistry authoring) {
    this.authoring = Objects.requireNonNull(authoring, "authoring");
  }

  /** Returns scalar userTypeId, or null if the path is unknown or not a scalar leaf. */
  public String resolveScalarUserTypeId(EntityAuthoring rootEntity, String propertyPath) {
    if (rootEntity == null) return null;
    CacheKey key = new CacheKey(rootEntity.type(), propertyPath);
    return cache.computeIfAbsent(key, k -> resolveScalarUserTypeIdNoCache(rootEntity, propertyPath));
  }

  private String resolveScalarUserTypeIdNoCache(EntityAuthoring rootEntity, String propertyPath) {
    if (propertyPath == null || propertyPath.isBlank()) return null;
    String[] parts = propertyPath.split("\\.", -1);
    EntityAuthoring current = rootEntity;

    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      FieldDef fd = current.fields().get(part);
      if (fd == null) return null;

      boolean last = (i == parts.length - 1);
      TypeRef tr = fd.type();
      if (last) {
        if (tr instanceof ScalarTypeRef s) return s.userTypeId();
        return null;
      }

      if (tr instanceof RefTypeRef rr) {
        current = authoring.getEntityAuthoring(rr.refEntityAuthoringId());
      } else {
        return null;
      }
    }
    return null;
  }

  private record CacheKey(String rootType, String propertyPath) {}
}


