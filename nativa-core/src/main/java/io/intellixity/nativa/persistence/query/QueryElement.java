package io.intellixity.nativa.persistence.query;

public interface QueryElement {
  <Q> Q accept(QueryVisitor<Q> visitor);
}

