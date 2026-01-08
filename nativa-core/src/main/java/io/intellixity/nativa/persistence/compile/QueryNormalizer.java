package io.intellixity.nativa.persistence.compile;

import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.query.*;

import java.util.*;

/**
 * Query-only normalization pass (does NOT create a second filter AST model).
 *
 * Responsibilities:
 * - Resolve {@link QueryValues.Param} placeholders from {@link Query#params()}
 * - Keep the public filter tree as {@link QueryElement} (including {@link NotElement})
 *
 * Notes:
 * - NULL semantics (EQ/NE null -> IS NULL/IS NOT NULL) are handled by renderers.
 * - NOT(group) De Morgan is handled by renderers (or can be pushed down in a future pass that still returns QueryElement).
 */
public final class QueryNormalizer {

  public QueryElement normalize(EntityAuthoring root, Query query) {
    if (query == null) return null;
    return normalizeElement(root, query.filter(), query);
  }

  private QueryElement normalizeElement(EntityAuthoring root, QueryElement el, Query query) {
    if (el == null) return null;

    if (el instanceof Query q) {
      return normalize(root, q);
    }

    if (el instanceof NotElement n) {
      QueryElement child = normalizeElement(root, n.element(), query);
      if (child == n.element()) return n;
      return (child == null) ? null : new NotElement(child);
    }

    if (el instanceof LogicalGroup g) {
      List<QueryElement> in = g.elements();
      List<QueryElement> out = null;
      for (int i = 0; i < in.size(); i++) {
        QueryElement c = in.get(i);
        QueryElement nc = normalizeElement(root, c, query);
        if (nc == c) continue;
        if (out == null) out = new ArrayList<>(in);
        out.set(i, nc);
      }
      if (out == null) return g;
      // drop null children
      out.removeIf(Objects::isNull);
      if (out.isEmpty()) return null;
      if (out.size() == 1) return out.getFirst();
      return new LogicalGroup(g.clause(), out);
    }

    if (el instanceof Condition c) {
      Object v = eval(query, c.value());
      Object lo = eval(query, c.lower());
      Object hi = eval(query, c.upper());
      if (Objects.equals(v, c.value()) && Objects.equals(lo, c.lower()) && Objects.equals(hi, c.upper())) return c;
      return new Condition(c.property(), c.operator(), v, lo, hi, c.not());
    }

    throw new IllegalArgumentException("Unsupported QueryElement: " + el.getClass().getName());
  }

  private static Object eval(Query query, Object v) {
    if (v == null) return null;
    if (v instanceof QueryValues.Param p) {
      Query effective = (query == null) ? new Query() : query;
      return effective.param(p.name());
    }
    if (v instanceof List<?> l) {
      List<Object> out = new ArrayList<>(l.size());
      boolean changed = false;
      for (Object x : l) {
        Object nx = eval(query, x);
        changed |= (nx != x);
        out.add(nx);
      }
      return changed ? out : v;
    }
    if (v instanceof Collection<?> c) {
      List<Object> out = new ArrayList<>(c.size());
      boolean changed = false;
      for (Object x : c) {
        Object nx = eval(query, x);
        changed |= (nx != x);
        out.add(nx);
      }
      return changed ? out : v;
    }
    if (v instanceof Map<?, ?> m) {
      // Resolve params in nested JSON-like structures too (for JSON_VALUE_EQ etc).
      Map<Object, Object> out = new LinkedHashMap<>();
      boolean changed = false;
      for (var e : m.entrySet()) {
        Object k = e.getKey();
        Object oldVal = e.getValue();
        Object newVal = eval(query, oldVal);
        changed |= (newVal != oldVal);
        out.put(k, newVal);
      }
      return changed ? out : v;
    }
    return v;
  }
}


