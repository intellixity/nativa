package io.intellixity.nativa.persistence.jdbc.dml;

import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.compile.Bind;
import io.intellixity.nativa.persistence.dmlast.*;
import io.intellixity.nativa.persistence.pojo.PojoAccessor;
import io.intellixity.nativa.persistence.pojo.PojoAccessorRegistry;
import io.intellixity.nativa.persistence.pojo.Nulls;
import io.intellixity.nativa.persistence.query.QueryElement;
import io.intellixity.nativa.persistence.query.QueryFilters;

import java.util.*;

/** JDBC-family DML planner (mapping + POJO extraction). No DB-specific defaults. */
public final class JdbcDmlPlanner implements DmlPlanner {
  private final AuthoringRegistry authoring;
  private final PojoAccessorRegistry accessors;
  private final Set<String> tenantBoundaryKeys;

  public JdbcDmlPlanner(AuthoringRegistry authoring) {
    this(authoring, null, Set.of());
  }

  public JdbcDmlPlanner(AuthoringRegistry authoring, PojoAccessorRegistry accessors) {
    this(authoring, accessors, Set.of());
  }

  public JdbcDmlPlanner(AuthoringRegistry authoring, PojoAccessorRegistry accessors, Set<String> tenantBoundaryKeys) {
    this.authoring = authoring;
    this.accessors = accessors;
    this.tenantBoundaryKeys = normalizeKeySet(tenantBoundaryKeys);
  }

  @Override
  public InsertAst planInsert(EntityAuthoring ea, ViewDef view, Object pojo, boolean returningKey) {
    String table = requireTable(ea);
    List<ColumnBind> cols = extractColumns(ea, view, pojo, null);
    List<String> returning = returningKey ? keyColumns(ea, view) : List.of();
    return new InsertAst(table, cols, returning);
  }

  @Override
  public UpdateAst planUpdateById(EntityAuthoring ea, ViewDef view, Object pojo) {
    String table = requireTable(ea);
    List<ColumnBind> sets = extractUpdateSets(ea, view, pojo);
    String keyField = requireSingleKeyFieldOrId(ea);
    Object id = get(ea, pojo, keyField);
    QueryElement where = QueryFilters.eq(keyField, id);
    return new UpdateAst(table, sets, where);
  }

  @Override
  public UpdateAst planUpdateByCriteria(EntityAuthoring ea, ViewDef view, Object pojo, QueryElement where) {
    String table = requireTable(ea);
    List<ColumnBind> sets = extractUpdateSets(ea, view, pojo);
    return new UpdateAst(table, sets, where);
  }

  @Override
  public DeleteAst planDeleteByCriteria(EntityAuthoring ea, ViewDef view, QueryElement where) {
    return new DeleteAst(requireTable(ea), where);
  }

  @Override
  public UpsertAst planUpsert(EntityAuthoring ea, ViewDef view, Object pojo, boolean returningKey) {
    InsertAst ins = planInsert(ea, view, pojo, returningKey);
    List<String> conflictCols = keyColumns(ea, view);
    Set<String> conflictSet = new HashSet<>(conflictCols);

    Set<String> tenantCols = tenantBoundaryColumns(ea, view);
    List<String> updateCols = new ArrayList<>();
    for (ColumnBind cb : ins.columns()) {
      if (!conflictSet.contains(cb.column())) updateCols.add(cb.column());
    }
    if (!tenantCols.isEmpty()) updateCols.removeIf(tenantCols::contains);
    return new UpsertAst(ins, conflictCols, updateCols);
  }

  private static String requireTable(EntityAuthoring ea) {
    String t = ea.source();
    if (t == null || t.isBlank()) throw new IllegalArgumentException("Entity authoring has no table source: " + ea.type());
    return t;
  }

  private List<String> keyColumns(EntityAuthoring ea, ViewDef view) {
    List<String> cols = new ArrayList<>();
    for (var e : ea.fields().entrySet()) {
      if (!e.getValue().key()) continue;
      String col = ViewMappings.ref(view, e.getKey());
      if (col != null) cols.add(col);
    }
    return cols;
  }

  private static String requireSingleKeyFieldOrId(EntityAuthoring ea) {
    String key = null;
    for (var e : ea.fields().entrySet()) {
      if (!e.getValue().key()) continue;
      if (key != null) {
        throw new IllegalArgumentException("Composite key not supported for updateById: " + ea.type());
      }
      key = e.getKey();
    }
    // Back-compat fallback: allow legacy entities that didn't mark key field.
    return (key != null) ? key : "id";
  }

  private List<ColumnBind> extractColumns(EntityAuthoring ea, ViewDef view, Object pojo, Set<String> fieldsToInclude) {
    List<ColumnBind> out = new ArrayList<>();
    Nulls nulls = (pojo instanceof Nulls n) ? n : null;
    for (var fe : ea.fields().entrySet()) {
      String field = fe.getKey();
      if (fieldsToInclude != null && !fieldsToInclude.contains(field)) continue;

      FieldDef fd = fe.getValue();
      TypeRef tr = fd.type();

      if (tr instanceof RefTypeRef) {
        // Nested mappings are explicit; no default policy for ref flattening.
        Object mv = view.mapping().get(field);
        if (!(mv instanceof Map<?, ?> nested)) continue;
        Object childObj = get(ea, pojo, field);
        if (childObj == null) continue;

        Object fieldsObj = nested.get("fields");
        if (fieldsObj instanceof Map<?, ?> fm) {
          EntityAuthoring childEa = resolveRefEntity(ea, field);
          for (var ne : fm.entrySet()) {
            String childField = String.valueOf(ne.getKey());
            String col = String.valueOf(ne.getValue());
            Object v = get(childEa, childObj, childField);
            if (v == null) continue;
            FieldDef cfd = (childEa == null) ? null : childEa.fields().get(childField);
            out.add(new ColumnBind(col, bindForValue(cfd, v)));
          }
        }
        continue;
      }

      String col = ViewMappings.ref(view, field);
      Object v = get(ea, pojo, field);
      if (v == null) {
        if (nulls == null || nulls.getNulls() == null || !nulls.getNulls().contains(field)) continue;
      }
      out.add(new ColumnBind(col, bindForValue(fd, v)));
    }
    return out;
  }

  private List<ColumnBind> extractUpdateSets(EntityAuthoring ea, ViewDef view, Object pojo) {
    List<ColumnBind> out = new ArrayList<>();
    Nulls nulls = (pojo instanceof Nulls n) ? n : null;
    for (var fe : ea.fields().entrySet()) {
      String field = fe.getKey();
      FieldDef fd = fe.getValue();
      if (fd != null && fd.key()) continue; // exclude key fields from SET
      if (isTenantBoundaryField(fd)) continue; // tenant boundary is insert-only

      TypeRef tr = fd.type();
      if (tr instanceof RefTypeRef) {
        // allow nested flattening if caller provided a nested object
        Object mv = view.mapping().get(field);
        if (!(mv instanceof Map<?, ?> nested)) continue;
        Object childObj = get(ea, pojo, field);
        if (childObj == null) continue;
        Object fieldsObj = nested.get("fields");
        if (fieldsObj instanceof Map<?, ?> fm) {
          EntityAuthoring childEa = resolveRefEntity(ea, field);
          for (var ne : fm.entrySet()) {
            String childField = String.valueOf(ne.getKey());
            String col = String.valueOf(ne.getValue());
            Object v = get(childEa, childObj, childField);
            if (v == null) continue;
            FieldDef cfd = (childEa == null) ? null : childEa.fields().get(childField);
            out.add(new ColumnBind(col, bindForValue(cfd, v)));
          }
        }
        continue;
      }

      String col = ViewMappings.ref(view, field);
      Object v = get(ea, pojo, field);
      if (v == null) {
        if (nulls == null || nulls.getNulls() == null || !nulls.getNulls().contains(field)) continue;
      }
      out.add(new ColumnBind(col, bindForValue(fd, v)));
    }
    return out;
  }

  private Object get(EntityAuthoring ea, Object pojo, String path) {
    if (pojo == null) return null;
    if (ea == null) throw new IllegalArgumentException("EntityAuthoring is required to extract path: " + path);
    if (accessors == null) {
      throw new IllegalArgumentException("No PojoAccessorRegistry configured for JdbcDmlPlanner (type=" + ea.type() + ")");
    }
    @SuppressWarnings("unchecked")
    PojoAccessor<Object> a = (PojoAccessor<Object>) accessors.accessorFor(ea.type());
    return a.get(pojo, path);
  }

  // Column resolution is centralized in ViewMappings (with underscore default policy).

  private EntityAuthoring resolveRefEntity(EntityAuthoring ea, String field) {
    FieldDef fd = ea.fields().get(field);
    if (fd == null) return null;
    if (fd.type() instanceof RefTypeRef rr) {
      return authoring.getEntityAuthoring(rr.refEntityAuthoringId());
    }
    return null;
  }

  private Bind bindForValue(FieldDef fd, Object value) {
    if (fd == null) return new Bind(value, "json");
    TypeRef tr = fd.type();

    if (tr instanceof ScalarTypeRef s) {
      return new Bind(value, s.userTypeId());
    }

    // Complex/container types are resolved by backend-specific UserTypeRegistry using canonical TypeIds.
    return new Bind(value, TypeIds.id(tr));
  }

  private boolean isTenantBoundaryField(FieldDef fd) {
    if (fd == null) return false;
    if (tenantBoundaryKeys.isEmpty()) return false;
    Map<String, Object> attrs = fd.attrs();
    Object raw = (attrs == null) ? null : attrs.get("governanceKey");
    if (raw == null) return false;
    for (String k : parseGovernanceKeys(raw)) {
      if (tenantBoundaryKeys.contains(k)) return true;
    }
    return false;
  }

  private Set<String> tenantBoundaryColumns(EntityAuthoring ea, ViewDef view) {
    if (tenantBoundaryKeys.isEmpty()) return Set.of();
    Set<String> out = new HashSet<>();
    for (var e : ea.fields().entrySet()) {
      String field = e.getKey();
      FieldDef fd = e.getValue();
      if (!isTenantBoundaryField(fd)) continue;
      String col = ViewMappings.ref(view, field);
      if (col != null && !col.isBlank()) out.add(col);
    }
    return out.isEmpty() ? Set.of() : Set.copyOf(out);
  }

  private static Set<String> normalizeKeySet(Set<String> keys) {
    if (keys == null || keys.isEmpty()) return Set.of();
    Set<String> out = new LinkedHashSet<>();
    for (String k : keys) {
      if (k == null) continue;
      String s = k.trim();
      if (!s.isEmpty()) out.add(s);
    }
    return Set.copyOf(out);
  }

  private static List<String> parseGovernanceKeys(Object raw) {
    if (raw == null) return List.of();
    if (raw instanceof String s) {
      String t = s.trim();
      return t.isEmpty() ? List.of() : List.of(t);
    }
    if (raw instanceof Iterable<?> it) {
      List<String> out = new ArrayList<>();
      for (Object o : it) {
        if (o == null) continue;
        String t = String.valueOf(o).trim();
        if (!t.isEmpty()) out.add(t);
      }
      return out.isEmpty() ? List.of() : List.copyOf(out);
    }
    String t = String.valueOf(raw).trim();
    return t.isEmpty() ? List.of() : List.of(t);
  }

}


