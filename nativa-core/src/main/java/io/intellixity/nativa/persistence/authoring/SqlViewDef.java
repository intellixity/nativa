package io.intellixity.nativa.persistence.authoring;

import java.util.List;

/** Query-style SQL view definition: FROM/JOIN + projection. */
public record SqlViewDef(
    /** Backend base query (SQL string for JDBC; pipeline/filter object for Mongo/ES). */
    Object sql,
    /** Optional projection (SQL SELECT projection; or pipeline stage object for Mongo/ES). */
    Object projection,
    /** If true, replace {schema} token in string sql/projection using engine-provided schema. */
    boolean schema,
    List<SqlAliasDef> aliases
) {
  public SqlViewDef {
    aliases = aliases == null ? List.of() : List.copyOf(aliases);
  }
}


