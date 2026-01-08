package io.intellixity.nativa.persistence.mongo;

import io.intellixity.nativa.persistence.authoring.ViewDef;

import java.util.Map;

/**
 * Mongo-specific view mapping resolution.
 * <p>
 * Policy:
 * - Only apply mappings that are explicitly declared in {@link ViewDef#mapping()}.
 * - If a field path is not explicitly mapped, callers should default to the original property path
 *   (camelCase -> camelCase).
 */
final class MongoViewMappingResolver {
  private MongoViewMappingResolver() {}

  /**
   * Resolve an explicitly-mapped field path to a Mongo document path/expression.
   * Returns null if the path is not explicitly mapped.
   */
  static String explicitRef(ViewDef view, String propertyPath) {
    if (view == null || propertyPath == null) return null;
    Map<String, Object> mapping = view.mapping();
    if (mapping == null || mapping.isEmpty()) return null;

    // Direct dot-path mapping key (e.g. "customer.id") or flat field (e.g. "firstName")
    Object direct = mapping.get(propertyPath);
    String ref = refFromSpec(direct);
    if (ref != null) return ref;

    // Nested mapping: parent -> { fields: { child: ... } }
    if (propertyPath.contains(".")) {
      String[] parts = propertyPath.split("\\.", 2);
      String parent = parts[0];
      String child = parts[1];
      Object parentSpec = mapping.get(parent);
      if (parentSpec instanceof Map<?, ?> pm) {
        Object fieldsObj = pm.get("fields");
        if (fieldsObj instanceof Map<?, ?> fm) {
          Object childSpec = fm.get(child);
          return refFromSpec(childSpec);
        }
      }
    }

    return null;
  }

  private static String refFromSpec(Object spec) {
    if (spec == null) return null;
    if (spec instanceof String s) return s;
    if (spec instanceof Map<?, ?> m) {
      Object ref = m.get("ref");
      if (ref != null) return String.valueOf(ref);
      Object col = m.get("column");
      if (col != null) return String.valueOf(col);
    }
    return null;
  }
}


