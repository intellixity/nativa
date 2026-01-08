package io.intellixity.nativa.persistence.query;

import io.intellixity.nativa.persistence.query.aggregation.GroupBy;

public interface QueryVisitor<Q> {
  Q visit(Condition condition);
  Q visit(LogicalGroup group);
  Q visit(NotElement not);
  Q visit(GroupBy groupBy);
}

