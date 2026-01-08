package io.intellixity.nativa.persistence.query.aggregation;

import io.intellixity.nativa.persistence.query.QueryElement;
import io.intellixity.nativa.persistence.query.QueryVisitor;

import java.util.*;

public record GroupBy(List<String> fields) implements QueryElement {
  @Override
  public <Q> Q accept(QueryVisitor<Q> visitor) { return visitor.visit(this); }

  public static GroupBy of(String... fields) { return new GroupBy(List.of(fields)); }
}

