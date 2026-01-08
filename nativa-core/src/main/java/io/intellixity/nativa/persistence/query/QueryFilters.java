package io.intellixity.nativa.persistence.query;

import java.util.*;

public final class QueryFilters {
  private QueryFilters() {}

  public static Condition eq(String property, Object value) { return Condition.of(property, Operator.EQ, value); }
  public static Condition ne(String property, Object value) { return Condition.of(property, Operator.NE, value); }
  public static Condition gt(String property, Object value) { return Condition.of(property, Operator.GT, value); }
  public static Condition ge(String property, Object value) { return Condition.of(property, Operator.GE, value); }
  public static Condition lt(String property, Object value) { return Condition.of(property, Operator.LT, value); }
  public static Condition le(String property, Object value) { return Condition.of(property, Operator.LE, value); }

  public static Condition in(String property, Collection<?> values) { return Condition.of(property, Operator.IN, values); }
  public static Condition nin(String property, Collection<?> values) { return Condition.of(property, Operator.NIN, values); }

  public static Condition range(String property, Object lower, Object upper) { return Condition.range(property, lower, upper); }

  public static Condition like(String property, Object value) { return Condition.of(property, Operator.LIKE, value); }

  /** Array/collection contains semantics. Dialects define exact meaning; default is \"contains all\". */
  public static Condition arrayContains(String property, Object values) { return Condition.of(property, Operator.ARRAY_CONTAINS, values); }

  /** Negated array/collection contains semantics. */
  public static Condition arrayNotContains(String property, Object values) { return Condition.of(property, Operator.ARRAY_NOT_CONTAINS, values); }

  /** Array/collection overlaps semantics (any match). */
  public static Condition arrayOverlaps(String property, Object values) { return Condition.of(property, Operator.ARRAY_OVERLAPS, values); }

  /** Negated overlaps semantics. */
  public static Condition arrayNotOverlaps(String property, Object values) { return Condition.of(property, Operator.ARRAY_NOT_OVERLAPS, values); }

  public static Condition jsonPathExists(String property, String jsonPath) { return Condition.of(property, Operator.JSON_PATH_EXISTS, jsonPath); }

  /** JSON path value equals: value should be JSON-serializable; jsonPath like \"$.a.b\". */
  public static Condition jsonValueEq(String property, String jsonPath, Object value) {
    return Condition.of(property, Operator.JSON_VALUE_EQ, Map.of("path", jsonPath, "value", value));
  }

  public static LogicalGroup and(QueryElement... elements) {
    return new LogicalGroup(Clause.AND, List.of(elements));
  }

  public static LogicalGroup or(QueryElement... elements) {
    return new LogicalGroup(Clause.OR, List.of(elements));
  }

  public static NotElement not(QueryElement element) {
    return new NotElement(element);
  }
}

