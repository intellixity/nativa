package io.intellixity.nativa.persistence.query;

import java.util.*;

public final class Condition implements QueryElement {
  private final String property;
  private final Operator operator;
  private final Object value;
  private final Object lower;
  private final Object upper;
  private final boolean not;

  public Condition(String property, Operator operator, Object value, Object lower, Object upper, boolean not) {
    this.property = Objects.requireNonNull(property, "property");
    this.operator = Objects.requireNonNull(operator, "operator");
    this.value = value;
    this.lower = lower;
    this.upper = upper;
    this.not = not;
  }

  public String property() { return property; }
  public Operator operator() { return operator; }
  public Object value() { return value; }
  public Object lower() { return lower; }
  public Object upper() { return upper; }
  public boolean not() { return not; }

  public Condition negate() {
    // Prefer operator negation where possible, else toggle not.
    return new Condition(property, operator, value, lower, upper, !not);
  }

  @Override
  public <Q> Q accept(QueryVisitor<Q> visitor) { return visitor.visit(this); }

  // ---------- JSON-ish parsing support (Map/List) ----------

  @SuppressWarnings("unchecked")
  public static QueryElement parseAny(Object o) {
    if (o == null) return null;
    if (o instanceof QueryElement qe) return qe;

    if (o instanceof Map<?,?> m) {
      if (m.containsKey("clause")) return LogicalGroup.fromMap((Map<String,Object>) m);
      if (m.containsKey("operator") && m.containsKey("property")) return Condition.fromMap((Map<String,Object>) m);
      throw new IllegalArgumentException("Unknown map query element: " + m.keySet());
    }

    if (o instanceof List<?> list) {
      // Nested group list format: [ {clause:AND}, <elem>, <elem>, [ {clause:OR}, ... ] ]
      return LogicalGroup.fromList((List<Object>) list);
    }

    throw new IllegalArgumentException("Unsupported query element: " + o.getClass());
  }

  public static Condition of(String property, Operator operator, Object value) {
    return new Condition(property, operator, value, null, null, false);
  }

  public static Condition range(String property, Object lower, Object upper) {
    return new Condition(property, Operator.RANGE, null, lower, upper, false);
  }

  public static Condition fromMap(Map<String,Object> m) {
    String property = String.valueOf(m.get("property"));
    Operator op = Operator.valueOf(String.valueOf(m.get("operator")).toUpperCase());
    boolean not = Boolean.parseBoolean(String.valueOf(m.getOrDefault("not", "false")));

    Object value = m.get("value");
    Object lower = m.get("lower");
    Object upper = m.get("upper");

    // Support param reference: { "param": "tenantId" }
    value = QueryValues.maybeParam(value);
    lower = QueryValues.maybeParam(lower);
    upper = QueryValues.maybeParam(upper);

    if (op == Operator.RANGE) {
      if (lower == null) lower = m.get("from");
      if (upper == null) upper = m.get("to");
      lower = QueryValues.maybeParam(lower);
      upper = QueryValues.maybeParam(upper);
      return new Condition(property, op, null, lower, upper, not);
    }

    return new Condition(property, op, value, null, null, not);
  }
}

