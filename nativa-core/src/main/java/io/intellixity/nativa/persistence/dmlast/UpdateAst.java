package io.intellixity.nativa.persistence.dmlast;

import io.intellixity.nativa.persistence.query.QueryElement;

import java.util.List;

public record UpdateAst(
    String table,
    List<ColumnBind> sets,
    QueryElement where
) implements DmlAst {
  public UpdateAst {
    sets = sets == null ? List.of() : List.copyOf(sets);
  }
}



