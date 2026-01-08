package io.intellixity.nativa.persistence.dmlast;

import java.util.List;

public record InsertAst(
    String table,
    List<ColumnBind> columns,
    List<String> returningColumns
) implements DmlAst {
  public InsertAst {
    columns = columns == null ? List.of() : List.copyOf(columns);
    returningColumns = returningColumns == null ? List.of() : List.copyOf(returningColumns);
  }
}





