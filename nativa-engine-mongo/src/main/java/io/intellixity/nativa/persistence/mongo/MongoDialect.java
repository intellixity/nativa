package io.intellixity.nativa.persistence.mongo;

import org.bson.Document;
import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.SqlViewDef;
import io.intellixity.nativa.persistence.authoring.ViewDef;
import io.intellixity.nativa.persistence.compile.PropertyTypeResolver;
import io.intellixity.nativa.persistence.dmlast.*;
import io.intellixity.nativa.persistence.query.OffsetPage;
import io.intellixity.nativa.persistence.query.Page;
import io.intellixity.nativa.persistence.query.QueryElement;
import io.intellixity.nativa.persistence.query.SortField;
import io.intellixity.nativa.persistence.spi.sql.Dialect;

import java.util.*;

/** Mongo dialect: merges authoring base native (filter/pipeline) with QueryElement and renders DML. */
public final class MongoDialect implements Dialect<MongoStatement> {
  @Override public String id() { return "mongo"; }

  @Override
  public MongoStatement mergeSelect(EntityAuthoring ea, ViewDef view, QueryElement filter,
                                   List<SortField> sort, Page page, Map<String, Object> params,
                                   PropertyTypeResolver types) {
    String collection = requireCollection(ea);
    NativeBase base = base(view);

    if (base.pipeline != null) {
      Document sortDoc = sortDoc(view, sort);
      return new MongoStatement(MongoStatement.Kind.AGGREGATE, collection, null, filter, List.copyOf(base.pipeline),
          sortDoc, skip(page), limit(page), null, false);
    }

    Document sortDoc = sortDoc(view, sort);
    return new MongoStatement(MongoStatement.Kind.FIND, collection, base.filter, filter, null,
        sortDoc, skip(page), limit(page), null, false);
  }

  @Override
  public MongoStatement mergeCount(EntityAuthoring ea, ViewDef view, QueryElement filter, Map<String, Object> params,
                                   PropertyTypeResolver types) {
    String collection = requireCollection(ea);
    NativeBase base = base(view);

    if (base.pipeline != null) {
      return new MongoStatement(MongoStatement.Kind.AGGREGATE, collection, null, filter, List.copyOf(base.pipeline),
          null, null, null, null, false);
    }

    return new MongoStatement(MongoStatement.Kind.COUNT, collection, base.filter, filter, null,
        null, null, null, null, false);
  }

  @Override
  public MongoStatement renderDml(EntityAuthoring ea, ViewDef view, DmlAst dml, PropertyTypeResolver types) {
    String collection = requireCollection(ea);

    if (dml instanceof InsertAst) {
      return new MongoStatement(MongoStatement.Kind.INSERT_ONE, collection, null, null, null, null, null, null,
          dml, false);
    }

    if (dml instanceof UpdateAst) {
      // caller decides updateOne vs updateMany; default to UPDATE_MANY
      return new MongoStatement(MongoStatement.Kind.UPDATE_MANY, collection, null, null, null, null, null, null,
          dml, false);
    }

    if (dml instanceof DeleteAst) {
      return new MongoStatement(MongoStatement.Kind.DELETE_MANY, collection, null, null, null, null, null, null,
          dml, false);
    }

    if (dml instanceof UpsertAst) {
      return new MongoStatement(MongoStatement.Kind.UPDATE_ONE, collection, null, null, null, null, null, null,
          dml, true);
    }

    throw new IllegalArgumentException("Unsupported DML AST: " + dml.getClass().getName());
  }

  // ---- helpers ----

  private static String requireCollection(EntityAuthoring ea) {
    String c = ea.source();
    if (c == null || c.isBlank()) throw new IllegalArgumentException("Entity authoring has no collection source: " + ea.type());
    return c;
  }

  private record NativeBase(Document filter, List<Document> pipeline) {}

  private static NativeBase base(ViewDef view) {
    SqlViewDef sv = view.sqlView();
    if (sv == null) return new NativeBase(new Document(), null);

    Object sql = sv.sql();
    Object proj = sv.projection();
    if (sql == null && proj == null) return new NativeBase(new Document(), null);

    boolean pipelineMode = false;
    if (proj != null) pipelineMode = true;
    if (sql instanceof List<?>) pipelineMode = true;
    if (sql instanceof Map<?, ?> m) {
      for (Object k : m.keySet()) {
        if (k != null && String.valueOf(k).startsWith("$")) { pipelineMode = true; break; }
      }
    }

    if (pipelineMode) {
      List<Document> out = new ArrayList<>();
      addStages(out, sql);
      addStages(out, proj);
      return new NativeBase(null, out);
    }

    if (sql instanceof Map<?, ?> m) return new NativeBase(toDoc(m), null);
    return new NativeBase(new Document(), null);
  }

  private static void addStages(List<Document> out, Object o) {
    if (o == null) return;
    if (o instanceof List<?> l) {
      for (Object x : l) out.add(toDoc(x));
      return;
    }
    // map or scalar -> treat as one stage
    out.add(toDoc(o));
  }

  private static Integer skip(Page page) {
    if (page instanceof OffsetPage op) return op.offset();
    return null;
  }

  private static Integer limit(Page page) {
    if (page == null) return null;
    return page.limit();
  }

  private static Document sortDoc(ViewDef view, List<SortField> sort) {
    if (sort == null || sort.isEmpty()) return null;
    Document d = new Document();
    for (SortField sf : sort) {
      String path = MongoViewMappingResolver.explicitRef(view, sf.field());
      String p = (path == null || path.isBlank()) ? sf.field() : path;
      d.put(p, sf.direction() == SortField.Direction.DESC ? -1 : 1);
    }
    return d;
  }

  @SuppressWarnings("unchecked")
  private static Document toDoc(Object o) {
    if (o == null) return new Document();
    if (o instanceof Document d) return d;
    if (o instanceof Map<?, ?> m) return new Document((Map<String, Object>) m);
    throw new IllegalArgumentException("Expected Map/Document for Mongo nativeQuery but got: " + o.getClass());
  }

  // DML value encoding + document building happens at execution time (engine), since it depends on UserTypeRegistry.
}


