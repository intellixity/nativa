package io.intellixity.nativa.persistence.governance;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Per-request/event governance context.\n
 *
 * Typical keys: tenantId, userId, orgId, etc.\n
 */
public interface GovernanceContext {

  /** Return a context value or null if absent. */
  Object get(String key);

  /** Stable cache identity for this context (used by LRU caches). */
  String cacheKey();

  /**
   * Context keys that define the tenant boundary for this request.\n
   *
   * Examples:\n
   * - enterprise=true  => {tenantId}\n
   * - enterprise=false => {tenantId, dealerId}\n
   *
   * If empty, callers may fall back to engine defaults.\n
   */
  default Set<String> tenantKeys() {
    return Set.of();
  }

  /** Return a required context value; throws if missing. */
  default Object getRequired(String key) {
    Objects.requireNonNull(key, "key");
    Object v = get(key);
    if (v == null) throw new IllegalStateException("Missing GovernanceContext key: " + key);
    return v;
  }

  /** Simple map-backed context. */
  static GovernanceContext of(Map<String, ?> values) {
    Map<String, ?> m = values == null ? Map.of() : Map.copyOf(values);
    // Default: stable-ish key based on map content hash (apps should prefer the explicit overload).
    String ck = "map:" + m.hashCode();
    return of(m, ck);
  }

  /** Simple map-backed context with an explicit stable cache key. */
  static GovernanceContext of(Map<String, ?> values, String cacheKey) {
    Map<String, ?> m = values == null ? Map.of() : Map.copyOf(values);
    String ck = (cacheKey == null || cacheKey.isBlank()) ? ("map:" + m.hashCode()) : cacheKey;
    return new GovernanceContext() {
      @Override public Object get(String key) { return m.get(key); }
      @Override public String cacheKey() { return ck; }
    };
  }
}


