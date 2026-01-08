package io.intellixity.nativa.persistence.mongo.bind;

import org.bson.Document;
import org.bson.types.ObjectId;
import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;
import io.intellixity.nativa.persistence.spi.bind.*;
import io.intellixity.nativa.persistence.compile.Bind;

import java.time.Instant;
import java.util.*;

/** Mongo binders (dialectId="mongo"). */
public final class MongoBinderProvider implements BinderProvider {
  @Override
  public String dialectId() {
    return "mongo";
  }

  @Override
  public Collection<Binder<?, ?>> binders() {
    // Order matters: coercion binders first, then default doc binder.
    return List.of(
        new MongoIdCoercionBinder(),
        new MongoUuidBinder(),
        new MongoInstantBinder(),
        new MongoDocumentPathBinder()
    );
  }

  /** Coerce id/_id values (UUID->String, ObjectId strings -> ObjectId). */
  static final class MongoIdCoercionBinder implements Binder<Document, Object> {
    @Override public Class<Document> targetType() { return Document.class; }
    @Override public Class<Object> valueType() { return Object.class; }

    @Override
    public boolean supports(BindContext ctx, Bind bind, Object encodedValue) {
      if (!(ctx instanceof MongoBindContext mc)) return false;
      String path = mc.path();
      if (!"_id".equals(path) && !"id".equals(path)) return false;
      return (encodedValue instanceof UUID) || (encodedValue instanceof String);
    }

    @Override
    public void bind(Document doc, BindContext ctx, Bind bind, Object encodedValue, UserTypeRegistry userTypes) {
      Object v = encodedValue;
      if (v instanceof UUID u) v = u.toString();
      else if (v instanceof String s) {
        if (ObjectId.isValid(s)) v = new ObjectId(s);
      }
      // apply via default binder path semantics
      new MongoDocumentPathBinder().bind(doc, ctx, bind, v, userTypes);
    }
  }

  /** Coerce uuid values to String for Mongo storage/querying (supports uuid and list<uuid>). */
  static final class MongoUuidBinder implements Binder<Document, Object> {
    @Override public Class<Document> targetType() { return Document.class; }
    @Override public Class<Object> valueType() { return Object.class; }

    @Override
    public boolean supports(BindContext ctx, Bind bind, Object encodedValue) {
      if (!(ctx instanceof MongoBindContext mc)) return false;
      if (mc.path() == null || mc.path().isBlank()) return false;
      if (bind == null) return false;
      String ut = bind.userTypeId();
      if (ut == null) return false;
      ut = ut.toLowerCase();
      return ut.equals("uuid") || ut.equals("list<uuid>") || ut.equals("set<uuid>");
    }

    @Override
    public void bind(Document doc, BindContext ctx, Bind bind, Object encodedValue, UserTypeRegistry userTypes) {
      Object v = coerceUuidValue(encodedValue);
      new MongoDocumentPathBinder().bind(doc, ctx, bind, v, userTypes);
    }

    private static Object coerceUuidValue(Object v) {
      if (v == null) return null;
      if (v instanceof UUID u) return u.toString();
      if (v instanceof String s) return s; // assume caller already normalized
      if (v instanceof List<?> l) {
        List<Object> out = new ArrayList<>(l.size());
        for (Object x : l) out.add(coerceUuidValue(x));
        return out;
      }
      if (v instanceof Set<?> s) {
        List<Object> out = new ArrayList<>(s.size());
        for (Object x : s) out.add(coerceUuidValue(x));
        return out;
      }
      return v;
    }
  }

  /** Coerce instant values to java.util.Date for Mongo storage/querying (supports instant and list<instant>). */
  static final class MongoInstantBinder implements Binder<Document, Object> {
    @Override public Class<Document> targetType() { return Document.class; }
    @Override public Class<Object> valueType() { return Object.class; }

    @Override
    public boolean supports(BindContext ctx, Bind bind, Object encodedValue) {
      if (!(ctx instanceof MongoBindContext mc)) return false;
      if (mc.path() == null || mc.path().isBlank()) return false;
      if (bind == null) return false;
      String ut = bind.userTypeId();
      if (ut == null) return false;
      ut = ut.toLowerCase();
      return ut.equals("instant") || ut.equals("list<instant>") || ut.equals("set<instant>");
    }

    @Override
    public void bind(Document doc, BindContext ctx, Bind bind, Object encodedValue, UserTypeRegistry userTypes) {
      Object v = coerceInstantValue(encodedValue);
      new MongoDocumentPathBinder().bind(doc, ctx, bind, v, userTypes);
    }

    private static Object coerceInstantValue(Object v) {
      if (v == null) return null;
      if (v instanceof java.util.Date d) return d;
      if (v instanceof Instant i) return java.util.Date.from(i);
      if (v instanceof Number n) return new java.util.Date(n.longValue());
      if (v instanceof String s) return java.util.Date.from(Instant.parse(s));
      if (v instanceof List<?> l) {
        List<Object> out = new ArrayList<>(l.size());
        for (Object x : l) out.add(coerceInstantValue(x));
        return out;
      }
      if (v instanceof Set<?> s) {
        List<Object> out = new ArrayList<>(s.size());
        for (Object x : s) out.add(coerceInstantValue(x));
        return out;
      }
      return v;
    }
  }

  /** Default binder: put value into Document at ctx.path (dot-path supported). */
  static final class MongoDocumentPathBinder implements Binder<Document, Object> {
    @Override public Class<Document> targetType() { return Document.class; }
    @Override public Class<Object> valueType() { return Object.class; }

    @Override
    public boolean supports(BindContext ctx, Bind bind, Object encodedValue) {
      return (ctx instanceof MongoBindContext mc) && mc.path() != null && !mc.path().isBlank();
    }

    @Override
    public void bind(Document doc, BindContext ctx, Bind bind, Object encodedValue, UserTypeRegistry userTypes) {
      if (!(ctx instanceof MongoBindContext mc)) throw new IllegalArgumentException("Expected MongoBindContext");
      setByPath(doc, mc.path(), encodedValue);
    }

    private static void setByPath(Document doc, String path, Object value) {
      if (!path.contains(".")) {
        doc.put(path, value);
        return;
      }
      String[] parts = path.split("\\.");
      Document cur = doc;
      for (int i = 0; i < parts.length - 1; i++) {
        String p = parts[i];
        Object next = cur.get(p);
        if (next instanceof Document nd) cur = nd;
        else {
          Document nd = new Document();
          cur.put(p, nd);
          cur = nd;
        }
      }
      cur.put(parts[parts.length - 1], value);
    }
  }
}


