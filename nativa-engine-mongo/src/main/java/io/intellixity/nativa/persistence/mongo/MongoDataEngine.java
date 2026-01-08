package io.intellixity.nativa.persistence.mongo;

import com.mongodb.ClientSessionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonValue;
import org.bson.Document;
import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.spi.bind.BindOpKind;
import io.intellixity.nativa.persistence.compile.Bind;
import io.intellixity.nativa.persistence.dmlast.*;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.exec.TxHandle;
import io.intellixity.nativa.persistence.spi.exec.AbstractDataEngine;
import io.intellixity.nativa.persistence.mapping.RowAdapter;
import io.intellixity.nativa.persistence.mapping.RowAdapters;
import io.intellixity.nativa.persistence.mapping.RowReader;
import io.intellixity.nativa.persistence.mongo.bind.DefaultMongoBindContext;
import io.intellixity.nativa.persistence.query.Query;
import io.intellixity.nativa.persistence.query.QueryElement;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Mongo backend engine using the official MongoDB Java sync driver.
 */
public final class MongoDataEngine extends AbstractDataEngine<MongoStatement, MongoHandle> {
  private final MongoClient client;
  private final MongoDatabase db;
  private final MongoDialect dialect;

  public MongoDataEngine(MongoHandle handle,
                         AuthoringRegistry authoring,
                         MongoDialect dialect,
                         DmlPlanner dmlPlanner,
                         Propagation defaultPropagation) {
    super(
        dialect,
        Objects.requireNonNull(handle, "handle"),
        Objects.requireNonNull(authoring, "authoring"),
        Objects.requireNonNull(dmlPlanner, "dmlPlanner"),
        defaultPropagation);

    this.client = handle.client();
    this.db = handle.client().getDatabase(Objects.requireNonNull(handle.namespace(), "database"));
    this.dialect = dialect;
  }

  /** Backward-compatible constructor: wraps raw client+database into a handle. */
  public MongoDataEngine(MongoClient client,
                         AuthoringRegistry authoring,
                         MongoDialect dialect,
                         DmlPlanner dmlPlanner,
                         String database) {
    this(client, authoring, dialect, dmlPlanner, database, Propagation.SUPPORTS);
  }

  /** Backward-compatible constructor: wraps raw client+database into a handle. */
  public MongoDataEngine(MongoClient client,
                         AuthoringRegistry authoring,
                         MongoDialect dialect,
                         DmlPlanner dmlPlanner,
                         String database,
                         Propagation defaultPropagation) {
    this(new MongoHandle("mongo", client, database, true), authoring, dialect, dmlPlanner, defaultPropagation);
  }

  @Override
  protected MongoStatement buildSelectStatement(EntityAuthoring ea, ViewDef view, Query effectiveQuery, QueryElement filter) {
    MongoStatement st = dialect.mergeSelect(ea, view, filter, effectiveQuery.sort(), effectiveQuery.page(), effectiveQuery.params(), propertyTypes());
    return finalizeReadStatement(ea, view, st, false);
  }

  @Override
  protected MongoStatement buildCountStatement(EntityAuthoring ea, ViewDef view, Query effectiveQuery, QueryElement filter) {
    MongoStatement st = dialect.mergeCount(ea, view, filter, effectiveQuery.params(), propertyTypes());
    return finalizeReadStatement(ea, view, st, true);
  }

  public record MongoTxHandle(ClientSession session) implements TxHandle {}

  @Override
  protected TxHandle begin() {
    ClientSession s = client.startSession(ClientSessionOptions.builder().build());
    s.startTransaction();
    return new MongoTxHandle(s);
  }

  @Override
  protected void commit(TxHandle tx) {
    MongoTxHandle m = (MongoTxHandle) tx;
    m.session.commitTransaction();
    m.session.close();
  }

  @Override
  protected void rollback(TxHandle tx) {
    MongoTxHandle m = (MongoTxHandle) tx;
    m.session.abortTransaction();
    m.session.close();
  }

  @Override
  protected <T> List<T> executeSelect(TxHandle txOrNull, ViewDef view, MongoStatement st, RowReader<T> reader) {
    MongoCollection<Document> col = db.getCollection(st.collection());
    ClientSession s = sessionOrNull();

    Iterable<Document> docs;
    if (st.kind() == MongoStatement.Kind.AGGREGATE) {
      docs = (s == null) ? col.aggregate(st.pipeline()) : col.aggregate(s, st.pipeline());
    } else {
      var find = (s == null) ? col.find(st.filter()) : col.find(s, st.filter());
      if (st.sort() != null && !st.sort().isEmpty()) find = find.sort(st.sort());
      if (st.skip() != null) find = find.skip(st.skip());
      if (st.limit() != null) find = find.limit(st.limit());
      docs = find;
    }

    List<T> out = new ArrayList<>();
    for (Document d : docs) {
      RowAdapter base = RowAdapters.fromMap(d, userTypes());
      RowAdapter row = new MongoViewRowAdapter(base, view);
      out.add(reader.read(row));
    }
    return out;
  }

  @Override
  protected long executeCount(TxHandle txOrNull, ViewDef view, MongoStatement st) {
    MongoCollection<Document> col = db.getCollection(st.collection());
    ClientSession s = sessionOrNull();

    if (st.kind() == MongoStatement.Kind.AGGREGATE) {
      Iterable<Document> docs = (s == null) ? col.aggregate(st.pipeline()) : col.aggregate(s, st.pipeline());
      for (Document d : docs) {
        Object n = d.get("n");
        if (n instanceof Number nn) return nn.longValue();
        if (n == null) return 0;
        return Long.parseLong(String.valueOf(n));
      }
      return 0;
    }

    return (s == null) ? col.countDocuments(st.filter()) : col.countDocuments(s, st.filter());
  }

  @Override
  protected Object executeInsertForId(TxHandle tx, EntityAuthoring ea, ViewDef view,
                                      InsertAst ast, MongoStatement st) {
    MongoCollection<Document> col = db.getCollection(st.collection());

    Document doc = encodeInsert(ea, view, ast);
    ClientSession s = sessionOrNull();
    InsertOneResult r = (s == null) ? col.insertOne(doc) : col.insertOne(s, doc);
    Object id = explicitId(view, ast);
    if (id == null) id = (r.getInsertedId() == null) ? doc.get("_id") : bsonToJava(r.getInsertedId());
    return id;
  }

  protected Object executeUpsertForId(TxHandle tx, EntityAuthoring ea, ViewDef view,
                                      UpsertAst ast, MongoStatement st) {
    MongoCollection<Document> col = db.getCollection(st.collection());
    ClientSession s = sessionOrNull();

    UpsertSpec spec = encodeUpsert(ea, view, ast);
    UpdateOptions opts = new UpdateOptions().upsert(true);
    UpdateResult r = (s == null) ? col.updateOne(spec.filter, spec.update, opts) : col.updateOne(s, spec.filter, spec.update, opts);

    Object id = spec.idHint;
    if (id == null && r.getUpsertedId() != null) id = bsonToJava(r.getUpsertedId());
    return id;
  }

  @Override
  protected long executeUpdate(TxHandle tx, EntityAuthoring ea, ViewDef view, UpdateAst ast, MongoStatement st) {
    MongoCollection<Document> col = db.getCollection(st.collection());
    ClientSession s = sessionOrNull();

    Document where = compileWhere(ea, view, ast.where(), BindOpKind.FILTER);
    Document update = encodeUpdate(view, ast);
    UpdateResult r = (st.kind() == MongoStatement.Kind.UPDATE_ONE)
        ? ((s == null) ? col.updateOne(where, update) : col.updateOne(s, where, update))
        : ((s == null) ? col.updateMany(where, update) : col.updateMany(s, where, update));
    return r.getModifiedCount();
  }

  @Override
  protected long executeDelete(TxHandle tx, EntityAuthoring ea, ViewDef view, DeleteAst ast, MongoStatement st) {
    MongoCollection<Document> col = db.getCollection(st.collection());
    ClientSession s = sessionOrNull();

    Document where = compileWhere(ea, view, ast.where(), BindOpKind.FILTER);
    DeleteResult r = (s == null) ? col.deleteMany(where) : col.deleteMany(s, where);
    return r.getDeletedCount();
  }

  // insertByCriteria removed for now

  private ClientSession sessionOrNull() {
    TxHandle tx = currentTxOrNull();
    return (tx instanceof MongoTxHandle m) ? m.session : null;
  }

  private Document encodeInsert(EntityAuthoring ea, ViewDef view, InsertAst ast) {
    Document doc = new Document();
    for (ColumnBind cb : ast.columns()) {
      Bind b = cb.bind();
      Object encoded = encode(b);
      var ctx = new DefaultMongoBindContext(BindOpKind.INSERT, cb.column());
      bindInto(doc, ctx, b, encoded);
    }
    // keep authoring backend-agnostic: if view maps the (single) key field to a non-_id field, mirror it into _id when present
    String keyField = singleKeyFieldOrNull(ea);
    if (keyField != null) {
      String idPath = ViewMappings.ref(view, keyField);
      if (idPath != null && !"_id".equals(idPath) && doc.get("_id") == null) {
        Object id = doc.get(idPath);
        if (id != null) doc.put("_id", id);
      }
    }
    return doc;
  }

  private static String singleKeyFieldOrNull(EntityAuthoring ea) {
    if (ea == null) return null;
    String key = null;
    for (var e : ea.fields().entrySet()) {
      if (!e.getValue().key()) continue;
      if (key != null) return null; // composite key: no single Mongo _id mirror
      key = e.getKey();
    }
    return key;
  }

  private record UpsertSpec(Document filter, Document update, Object idHint) {}

  private UpsertSpec encodeUpsert(EntityAuthoring ea, ViewDef view, UpsertAst ast) {
    InsertAst ins = ast.insert();
    Document filter = new Document();
    Object idHint = null;
    String keyField = singleKeyFieldOrNull(ea);
    String keyPath = (keyField == null) ? null : ViewMappings.ref(view, keyField);
    for (String col : ast.conflictColumns()) {
      ColumnBind cb = ins.columns().stream().filter(x -> Objects.equals(x.column(), col)).findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Upsert conflict column not found: " + col));
      Bind b = cb.bind();
      Object encoded = encode(b);
      var ctx = new DefaultMongoBindContext(BindOpKind.UPSERT_FILTER, col);
      bindInto(filter, ctx, b, encoded);
      if ("_id".equals(col) || (keyPath != null && Objects.equals(keyPath, col))) idHint = filter.get(col);
    }

    Document set = new Document();
    for (String col : ast.updateColumns()) {
      ColumnBind cb = ins.columns().stream().filter(x -> Objects.equals(x.column(), col)).findFirst().orElse(null);
      if (cb == null) continue;
      Bind b = cb.bind();
      Object encoded = encode(b);
      var ctx = new DefaultMongoBindContext(BindOpKind.UPSERT_SET, col);
      bindInto(set, ctx, b, encoded);
    }
    Document setOnInsert = encodeInsert(ea, view, ins);
    Document update = new Document("$set", set).append("$setOnInsert", setOnInsert);
    return new UpsertSpec(filter, update, idHint);
  }

  private Document encodeUpdate(ViewDef view, UpdateAst ast) {
    Document set = new Document();
    for (ColumnBind cb : ast.sets()) {
      Bind b = cb.bind();
      Object encoded = encode(b);
      var ctx = new DefaultMongoBindContext(BindOpKind.UPDATE_SET, cb.column());
      bindInto(set, ctx, b, encoded);
    }
    return new Document("$set", set);
  }

  private MongoStatement finalizeReadStatement(EntityAuthoring ea, ViewDef view, MongoStatement st, boolean isCount) {
    if (st == null) return null;

    QueryElement filter = st.filterExpr();
    Document compiled = (filter == null) ? new Document() : MongoQueryRenderer.toBson(
        ea, view, filter, propertyTypes(), userTypes(), binders(), BindOpKind.FILTER);

    if (st.kind() == MongoStatement.Kind.AGGREGATE) {
      List<Document> pipeline = new ArrayList<>(st.pipeline() == null ? List.of() : st.pipeline());
      if (compiled != null && !compiled.isEmpty()) pipeline.add(new Document("$match", compiled));
      if (isCount) {
        pipeline.add(new Document("$count", "n"));
      } else {
        Document sort = st.sort();
        if (sort != null && !sort.isEmpty()) pipeline.add(new Document("$sort", sort));
        Integer skip = st.skip();
        Integer limit = st.limit();
        if (skip != null && skip > 0) pipeline.add(new Document("$skip", skip));
        if (limit != null) pipeline.add(new Document("$limit", limit));
      }
      return new MongoStatement(MongoStatement.Kind.AGGREGATE, st.collection(), null, null, List.copyOf(pipeline),
          null, null, null, null, false);
    }

    Document merged = and(st.filter(), compiled);
    if (isCount) {
      return new MongoStatement(MongoStatement.Kind.COUNT, st.collection(), merged, null, null,
          null, null, null, null, false);
    }
    return new MongoStatement(MongoStatement.Kind.FIND, st.collection(), merged, null, null,
        st.sort(), st.skip(), st.limit(), null, false);
  }

  private static Document and(Document a, Document b) {
    if (a == null || a.isEmpty()) return (b == null) ? new Document() : b;
    if (b == null || b.isEmpty()) return a;
    return new Document("$and", List.of(a, b));
  }

  private Document compileWhere(EntityAuthoring ea, ViewDef view, QueryElement where, BindOpKind opKind) {
    if (where == null) return new Document();
    Document d = MongoQueryRenderer.toBson(ea, view, where, propertyTypes(), userTypes(), binders(), opKind);
    return (d == null) ? new Document() : d;
  }

  private static Object bsonToJava(BsonValue v) {
    if (v == null) return null;
    if (v.isObjectId()) return v.asObjectId().getValue();
    if (v.isString()) return v.asString().getValue();
    if (v.isInt32()) return v.asInt32().getValue();
    if (v.isInt64()) return v.asInt64().getValue();
    if (v.isBoolean()) return v.asBoolean().getValue();
    return v.toString();
  }

  private static Object explicitId(ViewDef view, InsertAst ast) {
    String idPath = ViewMappings.ref(view, "id");
    if (idPath == null) idPath = "id";
    for (ColumnBind cb : ast.columns()) {
      if (Objects.equals(cb.column(), idPath)) return cb.bind().value();
    }
    return null;
  }

  // insertByCriteria removed; setByPath/findById helpers removed.
}


