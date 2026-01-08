package io.intellixity.nativa.persistence.authoring;

import java.util.LinkedHashMap;
import java.util.Map;

/** Helpers to interpret {@link ViewDef#mapping()} entries as refs/labels for query views. */
public final class ViewMappings {
  private ViewMappings() {}

  /** Build propertyPath -> label map (default label = propertyPath). */
  public static Map<String, String> labels(ViewDef view) {
    Map<String, String> out = new LinkedHashMap<>();
    flatten(view.mapping(), "", out, true);
    return out;
  }

  /** Resolve a propertyPath to a result-set label (default label = propertyPath). */
  public static String label(ViewDef view, String propertyPath) {
    Object spec = findSpec(view.mapping(), propertyPath);
    if (spec instanceof Map<?, ?> m) {
      Object label = m.get("label");
      if (label != null) return String.valueOf(label);
    }
    return propertyPath;
  }

  /** Mapping mode hint for an object field. Supported: structured (default), blob. */
  public static String mode(ViewDef view, String propertyPath) {
    Object spec = findSpec(view.mapping(), propertyPath);
    if (spec instanceof Map<?, ?> m) {
      Object mode = m.get("mode");
      if (mode != null) return String.valueOf(mode);
    }
    return "structured";
  }

  /** Resolve a propertyPath to a SQL ref/expression (e.g. `b.policy_number`, `tenant_id`). */
  public static String ref(ViewDef view, String propertyPath) {
    Object spec = findSpec(view.mapping(), propertyPath);
    if (spec == null) return defaultRef(propertyPath);
    if (spec instanceof String s) return s;
    if (spec instanceof Map<?, ?> m) {
      Object r = m.get("ref");
      if (r != null) return String.valueOf(r);
      // fallback: if it looks like column mapping object
      Object c = m.get("column");
      if (c != null) return String.valueOf(c);
    }
    return defaultRef(propertyPath);
  }

  private static Object findSpec(Map<String, Object> mapping, String propertyPath) {
    if (propertyPath == null) return null;
    if (mapping.containsKey(propertyPath)) return mapping.get(propertyPath);

    // nested: parent -> {fields:{child:...}}
    if (propertyPath.contains(".")) {
      String[] parts = propertyPath.split("\\.", 2);
      Object parentSpec = mapping.get(parts[0]);
      if (parentSpec instanceof Map<?, ?> pm) {
        Object fieldsObj = pm.get("fields");
        if (fieldsObj instanceof Map<?, ?> fm) {
          @SuppressWarnings("unchecked")
          Map<String, Object> childMap = (Map<String, Object>) fm;
          return findSpec(childMap, parts[1]);
        }
      }
    }
    return null;
  }

  private static void flatten(Map<String, Object> mapping, String prefix, Map<String, String> out, boolean allowDefault) {
    for (var e : mapping.entrySet()) {
      String key = e.getKey();
      Object spec = e.getValue();

      // If the key itself is a dot path, treat it as full property path.
      String propertyPath = prefix.isEmpty() ? key : prefix + key;

      if (spec instanceof Map<?, ?> m) {
        Object fieldsObj = m.get("fields");
        if (fieldsObj instanceof Map<?, ?> fm) {
          @SuppressWarnings("unchecked")
          Map<String, Object> child = (Map<String, Object>) fm;
          flatten(child, propertyPath + ".", out, allowDefault);
          continue;
        }

        Object label = m.get("label");
        if (label != null) out.put(propertyPath, String.valueOf(label));
        else if (allowDefault) out.put(propertyPath, propertyPath);
        continue;
      }

      // string mapping: default label = property path
      if (allowDefault) out.put(propertyPath, propertyPath);
    }
  }

  /**
   * Default mapping policy: underscore.\n
   * - customerName -> customer_name\n
   * - customer.addressLine1 -> customer.address_line1 (per-segment)\n
   */
  private static String defaultRef(String propertyPath) {
    if (propertyPath == null) return null;
    if (propertyPath.isBlank()) return propertyPath;
    if (!propertyPath.contains(".")) return underscore(propertyPath);
    String[] parts = propertyPath.split("\\.");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) sb.append('.');
      sb.append(underscore(parts[i]));
    }
    return sb.toString();
  }

  private static String underscore(String s) {
    if (s == null || s.isEmpty()) return s;
    StringBuilder out = new StringBuilder(s.length() + 8);
    char prev = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0 && prev != '_' && !Character.isUpperCase(prev)) out.append('_');
        out.append(Character.toLowerCase(c));
      } else {
        out.append(c);
      }
      prev = c;
    }
    return out.toString();
  }
}


