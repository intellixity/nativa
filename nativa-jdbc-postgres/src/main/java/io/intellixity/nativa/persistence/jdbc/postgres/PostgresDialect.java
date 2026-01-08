package io.intellixity.nativa.persistence.jdbc.postgres;

import io.intellixity.nativa.persistence.compile.Bind;
import io.intellixity.nativa.persistence.jdbc.SqlStatement.ExecKind;
import io.intellixity.nativa.persistence.jdbc.SqlStatement;
import io.intellixity.nativa.persistence.jdbc.dialect.AbstractJdbcSqlDialect;
import io.intellixity.nativa.persistence.jdbc.dialect.JdbcDialect;
import io.intellixity.nativa.persistence.query.SeekPage;
import io.intellixity.nativa.persistence.query.SortField;

import java.util.List;
import java.util.Map;

/**
 * Postgres dialect implementation for JDBC.
 *
 * Keeps only Postgres-specific overrides and bind behavior.\n
 * Generic SQL rendering lives in {@link AbstractJdbcSqlDialect}.
 */
public final class PostgresDialect extends AbstractJdbcSqlDialect implements JdbcDialect {
  @Override public String id() { return "postgres"; }

  @Override
  protected ExecKind insertExecKind(List<String> returningColumns) {
    if (returningColumns == null || returningColumns.isEmpty()) return ExecKind.UPDATE;
    return ExecKind.QUERY_ONE_VALUE;
  }

  @Override
  protected String quoteIdent(String ident) {
    if (ident == null) return null;
    return "\"" + ident.replace("\"", "\"\"") + "\"";
  }

  @Override
  protected io.intellixity.nativa.persistence.jdbc.SqlStatement applyOffsetPage(
      io.intellixity.nativa.persistence.jdbc.SqlStatement base,
      io.intellixity.nativa.persistence.query.OffsetPage page,
      java.util.List<io.intellixity.nativa.persistence.query.SortField> sort
  ) {
    String sql = base.sql() + " LIMIT " + page.limit() + " OFFSET " + page.offset();
    return new io.intellixity.nativa.persistence.jdbc.SqlStatement(sql, base.binds());
  }

  @Override
  protected SqlStatement applySeekPage(SqlStatement base, SeekPage page, List<SortField> sort) {
    String sql = base.sql() + " LIMIT " + page.limit();
    return new SqlStatement(sql, base.binds(), base.execKind());
  }

  @Override
  protected String applyInsertReturning(String insertSql, List<String> returningColumns) {
    if (returningColumns == null || returningColumns.isEmpty()) return insertSql;
    List<String> ret = returningColumns.stream().map(this::quoteIdent).toList();
    return insertSql + " RETURNING " + String.join(", ", ret);
  }

  @Override
  protected io.intellixity.nativa.persistence.jdbc.SqlStatement renderUpsert(io.intellixity.nativa.persistence.dmlast.UpsertAst ups) {
    var ins = ups.insert();
    // Build base INSERT without RETURNING; add it after ON CONFLICT block.
    var insertBase = renderInsert(new io.intellixity.nativa.persistence.dmlast.InsertAst(ins.table(), ins.columns(), List.of()));

    if (ups.conflictColumns().isEmpty()) throw new IllegalArgumentException("Upsert has no conflict columns");

    StringBuilder sql = new StringBuilder(insertBase.sql());
    sql.append(" ON CONFLICT (");
    sql.append(String.join(", ", ups.conflictColumns().stream().map(this::quoteIdent).toList()));
    sql.append(") DO UPDATE SET ");
    List<String> updateCols = ups.updateColumns().isEmpty() ? ups.conflictColumns() : ups.updateColumns();
    sql.append(String.join(", ", updateCols.stream()
        .map(c -> quoteIdent(c) + " = EXCLUDED." + quoteIdent(c))
        .toList()));

    if (!ins.returningColumns().isEmpty()) {
      sql.append(" RETURNING ");
      sql.append(String.join(", ", ins.returningColumns().stream().map(this::quoteIdent).toList()));
    }
    ExecKind kind = ins.returningColumns().isEmpty() ? ExecKind.UPDATE : ExecKind.QUERY_ONE_VALUE;
    return new io.intellixity.nativa.persistence.jdbc.SqlStatement(sql.toString(), insertBase.binds(), kind);
  }

  @Override
  protected String renderArrayContains(String expr, List<Object> values, String userTypeId, boolean not, RenderCtx ctx) {
    // Postgres array containment: col @> ARRAY[...]
    // We bind the full RHS value as a single parameter (expected to be a List-like userType, e.g. list<string>).
    List<Object> rhs = (values == null) ? List.of() : List.copyOf(values);
    String p = ctx.add(new Bind(rhs, userTypeId));
    String sql = expr + " @> " + p;
    return not ? "NOT (" + sql + ")" : sql;
  }

  @Override
  protected String renderArrayOverlaps(String expr, List<Object> values, String userTypeId, boolean not, RenderCtx ctx) {
    List<Object> rhs = (values == null) ? List.of() : List.copyOf(values);
    String p = ctx.add(new Bind(rhs, userTypeId));
    String sql = expr + " && " + p;
    return not ? "NOT (" + sql + ")" : sql;
  }

  @Override
  protected String renderJsonPathExists(String expr, String jsonPath, boolean not, RenderCtx ctx) {
    String pgPath = pgTextArrayLiteral(jsonPath);
    String sql = expr + " #> " + pgPath + " IS NOT NULL";
    return not ? "NOT (" + sql + ")" : sql;
  }

  @Override
  protected String renderJsonValueEq(String expr, Object valueObj, boolean not, RenderCtx ctx) {
    // Expect valueObj as {path: "$.a.b", value: <any>}
    if (!(valueObj instanceof Map<?, ?> m)) {
      throw new IllegalArgumentException("JSON_VALUE_EQ expects value object {path,value}");
    }
    Object pathObj = m.get("path");
    Object valObj = m.get("value");
    if (pathObj == null) throw new IllegalArgumentException("JSON_VALUE_EQ requires 'path'");

    Object fragment = jsonFragment(String.valueOf(pathObj), valObj);
    String p = ctx.add(new Bind(fragment, "json"));
    String sql = expr + " @> " + p;
    return not ? "NOT (" + sql + ")" : sql;
  }

  private static String pgTextArrayLiteral(String jsonPath) {
    // Convert "$.a.b" or "a.b" to Postgres text[] literal: '{a,b}'
    String p = (jsonPath == null) ? "" : jsonPath.trim();
    if (p.startsWith("$.")) p = p.substring(2);
    if (p.startsWith("$")) p = p.substring(1);
    if (p.startsWith(".")) p = p.substring(1);
    if (p.isBlank()) return "'{}'";
    String[] parts = p.split("\\.");
    String joined = String.join(",", parts);
    return "'{" + joined + "}'";
  }

  private static Object jsonFragment(String jsonPath, Object value) {
    String p = (jsonPath == null) ? "" : jsonPath.trim();
    if (p.startsWith("$.")) p = p.substring(2);
    if (p.startsWith("$")) p = p.substring(1);
    if (p.startsWith(".")) p = p.substring(1);
    if (p.isBlank()) {
      return (value instanceof Map || value instanceof List) ? value : Map.of("value", value);
    }
    String[] parts = p.split("\\.");
    Object cur = value;
    for (int i = parts.length - 1; i >= 0; i--) {
      cur = Map.of(parts[i], cur);
    }
    return cur;
  }
}


