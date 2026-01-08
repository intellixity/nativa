package io.intellixity.nativa.persistence.authoring.yaml;

import io.intellixity.nativa.persistence.authoring.*;

import java.util.Map;

/** Tiny type DSL parser for V0 YAML. */
final class TypeRefParser {
  private TypeRefParser() {}

  static TypeRef parse(String s) {
    if (s == null || s.isBlank()) throw new IllegalArgumentException("type is blank");
    s = s.trim();

    if (s.startsWith("ref(") && s.endsWith(")")) {
      String id = s.substring("ref(".length(), s.length() - 1).trim();
      return new RefTypeRef(id, null);
    }

    // value types behave like references for typing purposes; actual definition may be inline or separate YAML.
    if (s.startsWith("value(") && s.endsWith(")")) {
      String id = s.substring("value(".length(), s.length() - 1).trim();
      return new RefTypeRef(id, null);
    }

    if (s.startsWith("list<") && s.endsWith(">")) {
      return new ListTypeRef(parse(s.substring(5, s.length() - 1)));
    }
    if (s.startsWith("set<") && s.endsWith(">")) {
      return new SetTypeRef(parse(s.substring(4, s.length() - 1)));
    }
    if (s.startsWith("array<") && s.endsWith(">")) {
      return new ArrayTypeRef(parse(s.substring(6, s.length() - 1)));
    }
    if (s.startsWith("map<") && s.endsWith(">")) {
      String inside = s.substring(4, s.length() - 1);
      int comma = findTopComma(inside);
      if (comma < 0) throw new IllegalArgumentException("map<K,V> requires comma: " + s);
      TypeRef k = parse(inside.substring(0, comma));
      TypeRef v = parse(inside.substring(comma + 1));
      return new MapTypeRef(k, v);
    }

    // scalar
    String scalarId = s.contains(".") ? s : s.toLowerCase();
    return new ScalarTypeRef(scalarId, Map.of());
  }

  private static int findTopComma(String s) {
    int depth = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '<' || c == '(') depth++;
      else if (c == '>' || c == ')') depth--;
      else if (c == ',' && depth == 0) return i;
    }
    return -1;
  }
}




