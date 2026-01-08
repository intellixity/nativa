package io.intellixity.nativa.persistence.dmlast;

import java.util.List;

public record UpsertAst(
    InsertAst insert,
    List<String> conflictColumns,
    List<String> updateColumns
) implements DmlAst {
  public UpsertAst {
    conflictColumns = conflictColumns == null ? List.of() : List.copyOf(conflictColumns);
    updateColumns = updateColumns == null ? List.of() : List.copyOf(updateColumns);
  }
}





