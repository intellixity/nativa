package io.intellixity.nativa.persistence.jdbc.dialect;

import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.RefTypeRef;
import io.intellixity.nativa.persistence.authoring.SqlViewDef;
import io.intellixity.nativa.persistence.authoring.TypeRef;
import io.intellixity.nativa.persistence.authoring.ViewDef;
import io.intellixity.nativa.persistence.compile.Bind;
import io.intellixity.nativa.persistence.compile.PropertyTypeResolver;
import io.intellixity.nativa.persistence.dmlast.*;
import io.intellixity.nativa.persistence.jdbc.SqlStatement;
import io.intellixity.nativa.persistence.jdbc.ViewSqlParamCompiler;
import io.intellixity.nativa.persistence.jdbc.SqlStatement.ExecKind;
import io.intellixity.nativa.persistence.query.*;
import io.intellixity.nativa.persistence.query.OffsetPage;
import io.intellixity.nativa.persistence.query.Page;
import io.intellixity.nativa.persistence.query.SortField;
import io.intellixity.nativa.persistence.query.QueryValidationException;

import java.util.*;

/**
 * JDBC-generic SQL dialect base.\n
 *
 * Provides common rendering for:\n
 * - select/count merging: base view SQL + QueryElement filter + sort + paging\n
 * - DML: insert/update/delete from DmlAst\n
 *
 * DB-specific dialects override hooks for quoting, paging, returning, and upsert syntax.\n
 */
public abstract class AbstractJdbcSqlDialect implements JdbcDialect {
  protected static final class RenderCtx {
    private int n = 1;
    private final List<Bind> binds = new ArrayList<>();
    public String add(Bind b) {
      binds.add(b);
      return ":b" + (n++);
    }
  }

  @Override
  public final SqlStatement mergeSelect(EntityAuthoring ea, ViewDef view, QueryElement filter,
                                        List<SortField> sort, Page page, Map<String, Object> params,
                                        PropertyTypeResolver types) {
    SqlStatement base = baseSelectSql(ea, view, params);
    SqlStatement withFilter = appendFilter(ea, base, view, filter, baseHasWhere(base.sql()), types);
    SqlStatement withSeek = (page instanceof SeekPage sp)
        ? appendSeekFilter(ea, withFilter, view, sort, sp, types)
        : withFilter;
    SqlStatement withSort = appendSort(withSeek, view, sort);
    return appendPage(withSort, page, sort);
  }

  @Override
  public final SqlStatement mergeCount(EntityAuthoring ea, ViewDef view, QueryElement filter, Map<String, Object> params,
                                       PropertyTypeResolver types) {
    SqlStatement base = baseSelectSql(ea, view, params);
    SqlStatement withFilter = appendFilter(ea, base, view, filter, baseHasWhere(base.sql()), types);
    String sql = "SELECT COUNT(1) FROM (" + withFilter.sql() + ") nativa_count";
    return new SqlStatement(sql, withFilter.binds());
  }

  @Override
  public final SqlStatement renderDml(EntityAuthoring ea, ViewDef view, DmlAst dml, PropertyTypeResolver types) {
    if (dml instanceof InsertAst ins) return renderInsert(ins);
    if (dml instanceof UpdateAst upd) return renderUpdate(ea, view, upd, types);
    if (dml instanceof DeleteAst del) return renderDelete(ea, view, del, types);
    if (dml instanceof UpsertAst ups) return renderUpsert(ups);
    throw new IllegalArgumentException("Unknown DmlAst: " + dml);
  }

  protected SqlStatement baseSelectSql(EntityAuthoring ea, ViewDef view, Map<String, Object> params) {
    if (view.sqlView() != null) {
      SqlViewDef sv = view.sqlView();
      if (!(sv.sql() instanceof String baseSql) || baseSql.isBlank()) {
        throw new IllegalArgumentException("sqlView.sql must be a non-blank SQL string for JDBC view: " + view.id());
      }
      String projection = "*";
      if (sv.projection() instanceof String p && !p.isBlank()) projection = p;
      String trimmed = baseSql.trim();
      String lower = trimmed.toLowerCase(Locale.ROOT);
      boolean fullQuery = lower.startsWith("select") || lower.startsWith("with");

      final String sql;
      if (fullQuery) {
        sql = "*".equals(projection)
            ? baseSql
            : ("SELECT " + projection + " FROM (" + baseSql + ") nativa_base");
      } else {
        sql = "SELECT " + projection + " " + baseSql;
      }
      List<Bind> binds = ViewSqlParamCompiler.bindsFor(sql, params);
      return new SqlStatement(sql, binds);
    }

    // Derived base SQL from source + mapping policy (keeps examples working without explicit sql)
    String source = ea.source();
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("EntityAuthoring has no source for JDBC view: " + view.id());
    }

    List<String> selectItems = new ArrayList<>();
    for (var fe : ea.fields().entrySet()) {
      String field = fe.getKey();
      TypeRef tr = fe.getValue().type();

      if (tr instanceof RefTypeRef) {
        // Nested refs require explicit field mapping.
        Object mv = view.mapping().get(field);
        if (mv instanceof Map<?, ?> nested) {
          Object fieldsObj = nested.get("fields");
          if (fieldsObj instanceof Map<?, ?> fm) {
            for (var ne : fm.entrySet()) {
              String childField = String.valueOf(ne.getKey());
              String col = String.valueOf(ne.getValue());
              String alias = field + "." + childField;
              selectItems.add(col + " AS " + quoteIdent(alias));
            }
          }
        }
        continue;
      }

      String col = io.intellixity.nativa.persistence.authoring.ViewMappings.ref(view, field);
      if (col == null || col.isBlank()) continue;
      selectItems.add(col + " AS " + quoteIdent(field));
    }

    if (selectItems.isEmpty()) selectItems.add("*");
    String sql = "SELECT " + String.join(", ", selectItems) + " FROM " + quoteIdent(source);
    return new SqlStatement(sql, List.of());
  }

  protected boolean baseHasWhere(String sql) {
    if (sql == null) return false;
    String s = sql.toLowerCase(Locale.ROOT);
    return s.contains(" where ");
  }

  protected SqlStatement appendFilter(EntityAuthoring ea, SqlStatement base, ViewDef view, QueryElement filter,
                                      boolean baseHasWhere, PropertyTypeResolver types) {
    if (filter == null) return base;
    RenderedPredicate rp = renderPredicate(ea, view, filter, types);
    if (rp.sql.isBlank()) return base;

    String joiner = baseHasWhere ? " AND " : " WHERE ";
    String sql = base.sql() + joiner + rp.sql;
    List<Bind> binds = new ArrayList<>(base.binds());
    binds.addAll(rp.binds);
    return new SqlStatement(sql, binds);
  }

  protected SqlStatement appendSort(SqlStatement base, ViewDef view, List<SortField> sort) {
    if (sort == null || sort.isEmpty()) return base;
    List<String> parts = new ArrayList<>();
    for (SortField sf : sort) {
      String expr = resolveSqlExpr(view, sf.field());
      if (expr == null) continue;
      parts.add(expr + (sf.direction() == SortField.Direction.DESC ? " DESC" : " ASC"));
    }
    if (parts.isEmpty()) return base;
    return new SqlStatement(base.sql() + " ORDER BY " + String.join(", ", parts), base.binds());
  }

  protected SqlStatement appendPage(SqlStatement base, Page page, List<SortField> sort) {
    if (page == null) return base;
    if (page instanceof OffsetPage op) {
      return applyOffsetPage(base, op, sort);
    }
    if (page instanceof SeekPage sp) {
      return applySeekPage(base, sp, sort);
    }
    return base;
  }

  /** Default is no-op; dialects override. */
  protected SqlStatement applyOffsetPage(SqlStatement base, OffsetPage page, List<SortField> sort) {
    return base;
  }

  /** Default is no-op; dialects override (Postgres LIMIT, MSSQL OFFSET/FETCH, etc.). */
  protected SqlStatement applySeekPage(SqlStatement base, SeekPage page, List<SortField> sort) {
    return base;
  }

  /** Append keyset predicate based on sort + seek.after(). */
  protected SqlStatement appendSeekFilter(EntityAuthoring ea, SqlStatement base, ViewDef view,
                                          List<SortField> sort, SeekPage seek, PropertyTypeResolver types) {
    if (seek == null) return base;
    Map<String, Object> after = seek.after();
    if (after == null || after.isEmpty()) return base; // first page
    if (sort == null || sort.isEmpty()) {
      throw new IllegalArgumentException("SeekPage requires sort fields");
    }

    List<Bind> binds = new ArrayList<>(base.binds());
    String predicate = renderSeekPredicate(ea, view, sort, after, types, binds);
    if (predicate == null || predicate.isBlank()) return base;

    String joiner = baseHasWhere(base.sql()) ? " AND " : " WHERE ";
    return new SqlStatement(base.sql() + joiner + predicate, binds, base.execKind());
  }

  private String renderSeekPredicate(EntityAuthoring ea, ViewDef view, List<SortField> sort,
                                     Map<String, Object> after, PropertyTypeResolver types, List<Bind> outBinds) {
    // (a > v1) OR (a = v1 AND b > v2) OR ...
    List<String> orTerms = new ArrayList<>();
    for (int i = 0; i < sort.size(); i++) {
      List<String> andTerms = new ArrayList<>();

      // equalities for 0..i-1
      for (int j = 0; j < i; j++) {
        SortField sf = sort.get(j);
        String expr = resolveSqlExpr(view, sf.field());
        if (expr == null) throw new IllegalArgumentException("Unknown sort field: " + sf.field());
        Object v = after.get(sf.field());
        if (v == null) throw new IllegalArgumentException("SeekPage.after missing value for sort field: " + sf.field());
        String ut = types.resolveScalarUserTypeId(ea, sf.field());
        if (ut == null) throw new QueryValidationException("Unknown scalar field path '" + sf.field() + "' in entity '" + ea.type() + "'");
        andTerms.add(expr + " = " + addSeekBind(outBinds, v, ut));
      }

      // comparison for i
      SortField sf = sort.get(i);
      String expr = resolveSqlExpr(view, sf.field());
      if (expr == null) throw new IllegalArgumentException("Unknown sort field: " + sf.field());
      Object v = after.get(sf.field());
      if (v == null) throw new IllegalArgumentException("SeekPage.after missing value for sort field: " + sf.field());
      String ut = types.resolveScalarUserTypeId(ea, sf.field());
      if (ut == null) throw new QueryValidationException("Unknown scalar field path '" + sf.field() + "' in entity '" + ea.type() + "'");
      String op = (sf.direction() == SortField.Direction.DESC) ? "<" : ">";
      andTerms.add(expr + " " + op + " " + addSeekBind(outBinds, v, ut));

      orTerms.add("(" + String.join(" AND ", andTerms) + ")");
    }
    return String.join(" OR ", orTerms);
  }

  private static String addSeekBind(List<Bind> out, Object value, String userTypeId) {
    Object v = coerceScalar(userTypeId, value);
    out.add(new Bind(v, userTypeId));
    return ":k" + out.size(); // name doesn't matter; count+order do
  }

  protected RenderedPredicate renderPredicate(EntityAuthoring ea, ViewDef view, QueryElement el, PropertyTypeResolver types) {
    if (el == null) return new RenderedPredicate("", List.of());
    RenderCtx ctx = new RenderCtx();
    String sql = renderPredicateSql(ea, view, el, ctx, types, false);
    return new RenderedPredicate(sql == null ? "" : sql, ctx.binds);
  }

  private String renderPredicateSql(EntityAuthoring ea, ViewDef view, QueryElement el, RenderCtx ctx,
                                    PropertyTypeResolver types, boolean negate) {
    if (el == null) return "";

    if (el instanceof NotElement n) {
      return renderPredicateSql(ea, view, n.element(), ctx, types, !negate);
    }

    if (el instanceof LogicalGroup g) {
      Clause clause = g.clause();
      if (clause == null) clause = Clause.AND;
      if (negate) clause = (clause == Clause.OR) ? Clause.AND : Clause.OR;
      List<String> childSql = new ArrayList<>();
      for (QueryElement c : g.elements()) {
        String s = renderPredicateSql(ea, view, c, ctx, types, negate);
        if (s == null || s.isBlank()) continue;
        childSql.add(s);
      }
      if (childSql.isEmpty()) return "";
      if (childSql.size() == 1) return childSql.getFirst();
      String sep = (clause == Clause.OR) ? " OR " : " AND ";
      return "(" + String.join(sep, childSql) + ")";
    }

    if (!(el instanceof Condition c)) {
      throw new IllegalArgumentException("Unsupported QueryElement in filter: " + el.getClass().getName());
    }

    String propertyPath = c.property();
    String expr = resolveSqlExpr(view, propertyPath);
    if (expr == null || expr.isBlank()) {
      throw new QueryValidationException(
          "Unknown field path '" + propertyPath + "' for view '" + (view == null ? "<null>" : view.id()) +
              "' (entity '" + (ea == null ? "<null>" : ea.type()) + "')"
      );
    }

    String userTypeId = (types == null) ? null : types.resolveScalarUserTypeId(ea, propertyPath);
    if (userTypeId == null) {
      throw new QueryValidationException("Unknown scalar field path '" + propertyPath + "' in entity '" + ea.type() + "'");
    }

    boolean not = c.not() ^ negate;
    Object value = c.value();

    return switch (c.operator()) {
      case EQ -> (value == null)
          ? nullCheckSql(expr, true, not)
          : unarySql(expr, "=", value, userTypeId, not, ctx);
      case NE -> (value == null)
          ? nullCheckSql(expr, false, not)
          : unarySql(expr, "<>", value, userTypeId, not, ctx);
      case GT -> unaryNonNull(expr, ">", value, userTypeId, not, ctx);
      case GE -> unaryNonNull(expr, ">=", value, userTypeId, not, ctx);
      case LT -> unaryNonNull(expr, "<", value, userTypeId, not, ctx);
      case LE -> unaryNonNull(expr, "<=", value, userTypeId, not, ctx);
      case LIKE -> unaryNonNull(expr, "LIKE", value, userTypeId, not, ctx);
      case IN -> listSql(expr, "IN", toList(value), userTypeId, not, ctx);
      case NIN -> listSql(expr, "NOT IN", toList(value), userTypeId, not, ctx);
      case RANGE -> {
        Object lo = c.lower();
        Object hi = c.upper();
        if (lo == null || hi == null) throw new IllegalArgumentException("RANGE requires non-null lower+upper for property '" + propertyPath + "'");
        yield betweenSql(expr, lo, hi, userTypeId, not, ctx);
      }
      case ARRAY_CONTAINS, ARRAY_NOT_CONTAINS -> {
        boolean opNegated = (c.operator() == Operator.ARRAY_NOT_CONTAINS);
        boolean effNot = not ^ opNegated;
        List<Object> vals = toList(value);
        if (vals.isEmpty()) {
          // \"contains []\" is always true, \"not contains []\" is always false; incorporate effNot.
          yield effNot ? "FALSE" : "TRUE";
        }
        yield renderArrayContains(expr, vals, userTypeId, effNot, ctx);
      }
      case ARRAY_OVERLAPS, ARRAY_NOT_OVERLAPS -> {
        boolean opNegated = (c.operator() == Operator.ARRAY_NOT_OVERLAPS);
        boolean effNot = not ^ opNegated;
        List<Object> vals = toList(value);
        if (vals.isEmpty()) {
          // \"overlaps []\" is always false, \"not overlaps []\" is always true; incorporate effNot.
          yield effNot ? "TRUE" : "FALSE";
        }
        yield renderArrayOverlaps(expr, vals, userTypeId, effNot, ctx);
      }
      case JSON_PATH_EXISTS -> renderJsonPathExists(expr, String.valueOf(value), not, ctx);
      case JSON_VALUE_EQ -> renderJsonValueEq(expr, value, not, ctx);
      default -> throw new IllegalArgumentException("Unsupported operator: " + c.operator());
    };
  }

  /**
   * Render array contains (\"column contains all values\") for dialects that support it.
   * Default throws; Postgres overrides to use array containment operators.
   */
  protected String renderArrayContains(String expr, List<Object> values, String userTypeId, boolean not, RenderCtx ctx) {
    throw new IllegalArgumentException("ARRAY_CONTAINS is not supported by dialect: " + id());
  }

  /** Render array overlaps (\"column has any of values\"). Default throws. */
  protected String renderArrayOverlaps(String expr, List<Object> values, String userTypeId, boolean not, RenderCtx ctx) {
    throw new IllegalArgumentException("ARRAY_OVERLAPS is not supported by dialect: " + id());
  }

  /** Render JSON path exists. Default throws. */
  protected String renderJsonPathExists(String expr, String jsonPath, boolean not, RenderCtx ctx) {
    throw new IllegalArgumentException("JSON_PATH_EXISTS is not supported by dialect: " + id());
  }

  /** Render JSON value equality. Default throws. */
  protected String renderJsonValueEq(String expr, Object valueObj, boolean not, RenderCtx ctx) {
    throw new IllegalArgumentException("JSON_VALUE_EQ is not supported by dialect: " + id());
  }

  private String nullCheckSql(String expr, boolean isNull, boolean not) {
    String sql = expr + (isNull ? " IS NULL" : " IS NOT NULL");
    return not ? "NOT (" + sql + ")" : sql;
  }

  private String unarySql(String expr, String op, Object value, String userTypeId, boolean not, RenderCtx ctx) {
    Object v = coerceScalar(userTypeId, value);
    String p = ctx.add(new Bind(v, userTypeId));
    String sql = expr + " " + op + " " + p;
    return not ? "NOT (" + sql + ")" : sql;
  }

  private String betweenSql(String expr, Object lower, Object upper, String userTypeId, boolean not, RenderCtx ctx) {
    Object lo = coerceScalar(userTypeId, lower);
    Object hi = coerceScalar(userTypeId, upper);
    String p1 = ctx.add(new Bind(lo, userTypeId));
    String p2 = ctx.add(new Bind(hi, userTypeId));
    String sql = expr + " BETWEEN " + p1 + " AND " + p2;
    return not ? "NOT (" + sql + ")" : sql;
  }

  private String listSql(String expr, String op, List<Object> vals, String userTypeId, boolean not, RenderCtx ctx) {
    if (vals == null || vals.isEmpty()) return not ? "TRUE" : "FALSE";
    List<String> ph = new ArrayList<>();
    for (Object x : vals) {
      Object v = coerceScalar(userTypeId, x);
      ph.add(ctx.add(new Bind(v, userTypeId)));
    }
    String sql = expr + " " + op + " (" + String.join(", ", ph) + ")";
    return not ? "NOT (" + sql + ")" : sql;
  }

  private static String unaryNonNull(String expr, String op, Object value, String userTypeId, boolean not, RenderCtx ctx) {
    if (value == null) throw new IllegalArgumentException(op + " requires non-null value");
    Object v = coerceScalar(userTypeId, value);
    String p = ctx.add(new Bind(v, userTypeId));
    String sql = expr + " " + op + " " + p;
    return not ? "NOT (" + sql + ")" : sql;
  }

  private static Object coerceScalar(String userTypeId, Object value) {
    if (value == null || userTypeId == null) return value;
    if ("uuid".equalsIgnoreCase(userTypeId)) {
      if (value instanceof java.util.UUID) return value;
      return java.util.UUID.fromString(String.valueOf(value).trim());
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> toList(Object v) {
    if (v == null) return List.of();
    if (v instanceof List<?> l) return (List<Object>) l;
    if (v instanceof Collection<?> c) return new ArrayList<>(c).stream().map(x -> (Object) x).toList();
    return List.of(v);
  }

  /**
   * Resolve a Query property path to an underlying SQL expression using view.mapping().\n
   * - Direct fields map to a column string.\n
   * - Nested fields use dot paths (e.g. customer.id) and map to nested mapping fields.\n
   */
  protected String resolveSqlExpr(ViewDef view, String propertyPath) {
    if (propertyPath == null) return null;
    // Allow direct dot-path keys (e.g. "order.customer.address") in mapping.\n
    Object direct = view.mapping().get(propertyPath);
    if (direct instanceof String s) return s;
    if (direct instanceof Map<?, ?> mm) {
      Object ref = mm.get("ref");
      if (ref != null) return String.valueOf(ref);
      Object c = mm.get("column");
      if (c != null) return String.valueOf(c);
    }

    if (propertyPath.contains(".")) {
      String[] parts = propertyPath.split("\\.", 2);
      String parent = parts[0];
      String child = parts[1];
      Object mv = view.mapping().get(parent);
      if (mv instanceof Map<?, ?> nested) {
        Object fieldsObj = ((Map<?, ?>) nested).get("fields");
        if (fieldsObj instanceof Map<?, ?> fm) {
          Object col = fm.get(child);
          if (col == null) return null;
          if (col instanceof String s) return s;
          if (col instanceof Map<?, ?> mm) {
            Object ref = mm.get("ref");
            if (ref != null) return String.valueOf(ref);
            Object c = mm.get("column");
            if (c != null) return String.valueOf(c);
          }
          return null;
        }
      }
      return null;
    }
    Object mv = view.mapping().get(propertyPath);
    if (mv instanceof String col) return col;
    if (mv instanceof Map<?, ?> mm) {
      Object ref = mm.get("ref");
      if (ref != null) return String.valueOf(ref);
      Object c = mm.get("column");
      if (c != null) return String.valueOf(c);
    }
    return null;
  }

  protected SqlStatement renderInsert(InsertAst ins) {
    if (ins.columns().isEmpty()) throw new IllegalArgumentException("Insert has no columns");
    List<String> cols = new ArrayList<>();
    List<String> ph = new ArrayList<>();
    List<Bind> binds = new ArrayList<>();
    int n = 1;
    for (ColumnBind cb : ins.columns()) {
      cols.add(quoteIdent(cb.column()));
      ph.add(":b" + (n++));
      binds.add(cb.bind());
    }
    String sql = "INSERT INTO " + quoteIdent(ins.table()) +
        " (" + String.join(", ", cols) + ") VALUES (" + String.join(", ", ph) + ")";
    sql = applyInsertReturning(sql, ins.returningColumns());
    return new SqlStatement(sql, binds, insertExecKind(ins.returningColumns()));
  }

  /**
   * Decide execution strategy for insert.\n
   *
   * <p>Default uses JDBC generated keys when a returning key was requested (i.e. returningColumns not empty).\n
   * Dialects that implement SQL-level returning (Postgres RETURNING, MSSQL OUTPUT) should override to return
   * {@link ExecKind#QUERY_ONE_VALUE}.</p>
   */
  protected ExecKind insertExecKind(List<String> returningColumns) {
    if (returningColumns == null || returningColumns.isEmpty()) return ExecKind.UPDATE;
    return ExecKind.UPDATE_GENERATED_KEYS;
  }

  protected String applyInsertReturning(String insertSql, List<String> returningColumns) {
    if (returningColumns == null || returningColumns.isEmpty()) return insertSql;
    // default: no returning support
    return insertSql;
  }

  protected SqlStatement renderUpdate(EntityAuthoring ea, ViewDef view, UpdateAst upd, PropertyTypeResolver types) {
    if (upd.sets().isEmpty()) throw new IllegalArgumentException("Update has no SET columns");
    List<String> sets = new ArrayList<>();
    List<Bind> binds = new ArrayList<>();
    int n = 1;
    for (ColumnBind cb : upd.sets()) {
      sets.add(quoteIdent(cb.column()) + " = :b" + (n++));
      binds.add(cb.bind());
    }
    String sql = "UPDATE " + quoteIdent(upd.table()) + " SET " + String.join(", ", sets);
    RenderedPredicate wp = renderPredicate(ea, view, upd.where(), types);
    if (!wp.sql.isBlank()) {
      sql += " WHERE " + stripParensIfAny(wp.sql);
      binds.addAll(wp.binds);
    }
    return new SqlStatement(sql, binds, ExecKind.UPDATE);
  }

  protected SqlStatement renderDelete(EntityAuthoring ea, ViewDef view, DeleteAst del, PropertyTypeResolver types) {
    String sql = "DELETE FROM " + quoteIdent(del.table());
    RenderedPredicate wp = renderPredicate(ea, view, del.where(), types);
    List<Bind> binds = new ArrayList<>();
    if (!wp.sql.isBlank()) {
      sql += " WHERE " + stripParensIfAny(wp.sql);
      binds.addAll(wp.binds);
    }
    return new SqlStatement(sql, binds, ExecKind.UPDATE);
  }

  /** DB-specific upsert. */
  protected abstract SqlStatement renderUpsert(UpsertAst ups);

  protected String stripParensIfAny(String s) {
    if (s == null) return null;
    String t = s.trim();
    if (t.startsWith("(") && t.endsWith(")")) return t.substring(1, t.length() - 1);
    return t;
  }

  protected abstract String quoteIdent(String ident);

  protected record RenderedPredicate(String sql, List<Bind> binds) {}
}


