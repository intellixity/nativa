package io.intellixity.nativa.persistence.query;

import java.util.Map;

public final class QueryValues {
  private QueryValues() {}

  public record Param(String name) {}

  public static Param param(String name) { return new Param(name); }

  @SuppressWarnings("unchecked")
  static Object maybeParam(Object v) {
    if (v == null) return null;
    if (v instanceof Param) return v;
    if (v instanceof Map<?,?> m) {
      Object p = m.get("param");
      if (p != null) return new Param(String.valueOf(p));
      p = m.get("$param");
      if (p != null) return new Param(String.valueOf(p));
    }
    return v;
  }
}

