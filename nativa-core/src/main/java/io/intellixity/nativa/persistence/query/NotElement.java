package io.intellixity.nativa.persistence.query;

import java.util.Objects;

/** Unary NOT for a query subtree (can wrap a {@link Condition} or a {@link LogicalGroup}). */
public final class NotElement implements QueryElement {
  private final QueryElement element;

  public NotElement(QueryElement element) {
    this.element = Objects.requireNonNull(element, "element");
  }

  public QueryElement element() { return element; }

  @Override
  public <Q> Q accept(QueryVisitor<Q> visitor) {
    return visitor.visit(this);
  }
}




