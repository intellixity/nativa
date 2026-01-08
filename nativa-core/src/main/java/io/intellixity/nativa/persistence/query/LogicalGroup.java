package io.intellixity.nativa.persistence.query;

import java.util.*;

public final class LogicalGroup implements QueryElement {
  private final Clause clause;
  private final List<QueryElement> elements;

  public LogicalGroup(Clause clause, List<QueryElement> elements) {
    this.clause = Objects.requireNonNull(clause, "clause");
    this.elements = List.copyOf(elements == null ? List.of() : elements);
  }

  public Clause clause() { return clause; }
  public List<QueryElement> elements() { return elements; }

  @Override
  public <Q> Q accept(QueryVisitor<Q> visitor) { return visitor.visit(this); }

  @SuppressWarnings("unchecked")
  static LogicalGroup fromMap(Map<String,Object> m) {
    Object raw = m.get("clause");
    Clause clause;
    if (raw == null) {
      clause = Clause.AND;
    } else {
      try {
        clause = Clause.valueOf(String.valueOf(raw).toUpperCase());
      } catch (Exception e) {
        clause = Clause.AND;
      }
    }
    Object els = m.get("elements");
    if (els instanceof List<?> list) return fromList((List<Object>) list, clause);
    return new LogicalGroup(clause, List.of());
  }

  static LogicalGroup fromList(List<Object> list) {
    if (list == null || list.isEmpty()) throw new IllegalArgumentException("Empty group list");
    Object first = list.getFirst();
    if (!(first instanceof Map<?,?> fm) || !fm.containsKey("clause")) {
      throw new IllegalArgumentException("Group list must start with {clause:AND|OR}");
    }
    Clause clause = Clause.valueOf(String.valueOf(((Map<?,?>) first).get("clause")).toUpperCase());
    return fromList(list.subList(1, list.size()), clause);
  }

  private static LogicalGroup fromList(List<Object> rest, Clause clause) {
    List<QueryElement> els = new ArrayList<>();
    for (Object o : rest) {
      QueryElement e = (QueryElement) Condition.parseAny(o);
      if (e != null) els.add(e);
    }
    return new LogicalGroup(clause, els);
  }
}

