package io.intellixity.nativa.persistence.mongo;

import org.bson.Document;
import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;
import io.intellixity.nativa.persistence.authoring.ViewDef;
import io.intellixity.nativa.persistence.spi.bind.DiscoveredBinderRegistry;
import io.intellixity.nativa.persistence.spi.bind.BindOpKind;
import io.intellixity.nativa.persistence.compile.Bind;
import io.intellixity.nativa.persistence.compile.PropertyTypeResolver;
import io.intellixity.nativa.persistence.mongo.bind.DefaultMongoBindContext;
import io.intellixity.nativa.persistence.query.*;
import io.intellixity.nativa.persistence.query.QueryValidationException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Renders Query-only filters ({@link QueryElement}) to MongoDB BSON ({@link Document}),
 * applying De Morgan for NOT groups and rewriting EQ/NE null to IS NULL/IS NOT NULL semantics.
 *
 * Values are encoded/bound through the generic binder pipeline (using {@link UserTypeRegistry} + {@link DiscoveredBinderRegistry}).
 */
final class MongoQueryRenderer {
  private MongoQueryRenderer() {}

  static Document toBson(EntityAuthoring ea,
                         ViewDef view,
                         QueryElement filter,
                         PropertyTypeResolver types,
                         UserTypeRegistry userTypes,
                         DiscoveredBinderRegistry binders,
                         BindOpKind opKind) {
    if (filter == null) return new Document();
    return render(ea, view, filter, types, userTypes, binders, opKind, false);
  }

  private static Document render(EntityAuthoring ea,
                                 ViewDef view,
                                 QueryElement el,
                                 PropertyTypeResolver types,
                                 UserTypeRegistry userTypes,
                                 DiscoveredBinderRegistry binders,
                                 BindOpKind opKind,
                                 boolean negate) {
    if (el == null) return new Document();

    if (el instanceof NotElement n) {
      return render(ea, view, n.element(), types, userTypes, binders, opKind, !negate);
    }

    if (el instanceof LogicalGroup g) {
      Clause clause = g.clause();
      if (clause == null) clause = Clause.AND;
      if (negate) clause = (clause == Clause.OR) ? Clause.AND : Clause.OR;

      List<Document> parts = new ArrayList<>();
      for (QueryElement child : g.elements()) {
        Document d = render(ea, view, child, types, userTypes, binders, opKind, negate);
        if (d != null && !d.isEmpty()) parts.add(d);
      }
      if (parts.isEmpty()) return new Document();
      if (parts.size() == 1) return parts.getFirst();
      return new Document((clause == Clause.OR) ? "$or" : "$and", parts);
    }

    if (!(el instanceof Condition c)) {
      throw new IllegalArgumentException("Unsupported QueryElement in filter: " + el.getClass().getName());
    }

    String propertyPath = c.property();
    String path = resolvePath(view, propertyPath);
    String userTypeId = (types == null) ? null : types.resolveScalarUserTypeId(ea, propertyPath);
    if (userTypeId == null) {
      throw new QueryValidationException("Unknown scalar field path '" + propertyPath + "' in entity '" + ea.type() + "'");
    }

    boolean not = c.not() ^ negate;
    Operator op = c.operator();
    if (op == Operator.ARRAY_NOT_CONTAINS) {
      op = Operator.ARRAY_CONTAINS;
      not = !not;
    }
    if (op == Operator.ARRAY_NOT_OVERLAPS) {
      op = Operator.ARRAY_OVERLAPS;
      not = !not;
    }

    Document positive = switch (op) {
      case EQ -> (c.value() == null)
          ? new Document(path, null)
          : new Document(path, boundValue(path, c.value(), userTypeId, userTypes, binders, opKind));
      case NE -> (c.value() == null)
          ? new Document(path, new Document("$ne", null))
          : new Document(path, new Document("$ne", boundValue(path, c.value(), userTypeId, userTypes, binders, opKind)));
      case GT -> new Document(path, new Document("$gt", boundValue(path, requireNonNull(op, c.value()), userTypeId, userTypes, binders, opKind)));
      case GE -> new Document(path, new Document("$gte", boundValue(path, requireNonNull(op, c.value()), userTypeId, userTypes, binders, opKind)));
      case LT -> new Document(path, new Document("$lt", boundValue(path, requireNonNull(op, c.value()), userTypeId, userTypes, binders, opKind)));
      case LE -> new Document(path, new Document("$lte", boundValue(path, requireNonNull(op, c.value()), userTypeId, userTypes, binders, opKind)));
      case IN -> new Document(path, new Document("$in", boundList(path, toList(c.value()), userTypeId, userTypes, binders, opKind)));
      case NIN -> new Document(path, new Document("$nin", boundList(path, toList(c.value()), userTypeId, userTypes, binders, opKind)));
      case RANGE -> new Document(path,
          new Document("$gte", boundValue(path, requireNonNull("RANGE.lower", c.lower()), userTypeId, userTypes, binders, opKind))
              .append("$lte", boundValue(path, requireNonNull("RANGE.upper", c.upper()), userTypeId, userTypes, binders, opKind)));
      case LIKE -> likePositive(path, String.valueOf(requireNonNull(op, c.value())));
      case ARRAY_CONTAINS -> new Document(path, new Document("$all",
          boundList(path, toList(c.value()), userTypeId, userTypes, binders, opKind)));
      case ARRAY_OVERLAPS -> new Document(path, new Document("$in",
          boundList(path, toList(c.value()), userTypeId, userTypes, binders, opKind)));
      case JSON_PATH_EXISTS -> new Document(resolveJsonPath(path, String.valueOf(c.value())), new Document("$exists", true));
      case JSON_VALUE_EQ -> jsonValueEqPositive(path, c.value(), userTypeId, userTypes, binders, opKind);
      default -> throw new IllegalArgumentException("Unsupported operator: " + op);
    };

    return not ? new Document("$nor", List.of(positive)) : positive;
  }

  private static Object requireNonNull(Object op, Object v) {
    if (v == null) throw new IllegalArgumentException(op + " requires non-null value");
    return v;
  }

  private static Object requireNonNull(String label, Object v) {
    if (v == null) throw new IllegalArgumentException(label + " requires non-null value");
    return v;
  }

  private static Document likePositive(String path, String likePattern) {
    // Translate SQL LIKE to regex. '%' -> '.*', '_' -> '.'
    StringBuilder re = new StringBuilder();
    re.append("^");
    for (int i = 0; i < likePattern.length(); i++) {
      char ch = likePattern.charAt(i);
      if (ch == '%') re.append(".*");
      else if (ch == '_') re.append(".");
      else re.append(Pattern.quote(String.valueOf(ch)));
    }
    re.append("$");
    return new Document(path, new Document("$regex", re.toString()));
  }

  private static Document jsonValueEqPositive(String basePath,
                                              Object valueObj,
                                              String userTypeId,
                                              UserTypeRegistry userTypes,
                                              DiscoveredBinderRegistry binders,
                                              BindOpKind opKind) {
    if (!(valueObj instanceof Map<?, ?> m)) {
      throw new IllegalArgumentException("JSON_VALUE_EQ expects value object {path,value}");
    }
    Object pathObj = m.get("path");
    Object valObj = m.get("value");
    if (pathObj == null) throw new IllegalArgumentException("JSON_VALUE_EQ requires 'path'");
    String fullPath = resolveJsonPath(basePath, String.valueOf(pathObj));
    Object v = boundValue(fullPath, valObj, userTypeId, userTypes, binders, opKind);
    return new Document(fullPath, v);
  }

  private static String resolveJsonPath(String basePath, String jsonPath) {
    String p = (jsonPath == null) ? "" : jsonPath.trim();
    if (p.startsWith("$.")) p = p.substring(2);
    if (p.startsWith("$")) p = p.substring(1);
    if (p.startsWith(".")) p = p.substring(1);
    if (p.isBlank()) return basePath;
    return basePath + "." + p;
  }

  private static String resolvePath(ViewDef view, String propertyPath) {
    if (view == null || propertyPath == null) return propertyPath;
    String ref = MongoViewMappingResolver.explicitRef(view, propertyPath);
    return (ref == null || ref.isBlank()) ? propertyPath : ref;
  }

  private static Object boundValue(String path,
                                   Object raw,
                                   String userTypeId,
                                   UserTypeRegistry userTypes,
                                   DiscoveredBinderRegistry binders,
                                   BindOpKind opKind) {
    Bind b = new Bind(raw, userTypeId);
    @SuppressWarnings("unchecked")
    var ut = (io.intellixity.nativa.persistence.authoring.UserType<Object>) userTypes.get(userTypeId);
    Object encoded = ut.encode(raw);
    Document tmp = new Document();
    binders.bind(tmp, new DefaultMongoBindContext(opKind, path), b, encoded, userTypes);
    return getByPath(tmp, path);
  }

  private static List<Object> boundList(String path,
                                        List<Object> raw,
                                        String userTypeId,
                                        UserTypeRegistry userTypes,
                                        DiscoveredBinderRegistry binders,
                                        BindOpKind opKind) {
    List<Object> out = new ArrayList<>(raw.size());
    for (Object r : raw) out.add(boundValue(path, r, userTypeId, userTypes, binders, opKind));
    return out;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> toList(Object v) {
    if (v == null) return List.of();
    if (v instanceof List<?> l) return (List<Object>) l;
    if (v instanceof Collection<?> c) return new ArrayList<>(c).stream().map(x -> (Object) x).toList();
    return List.of(v);
  }

  private static Object getByPath(Document root, String path) {
    if (root == null || path == null || path.isBlank()) return null;
    Object cur = root;
    for (String p : path.split("\\.")) {
      if (!(cur instanceof Map<?, ?> m)) return null;
      cur = m.get(p);
    }
    return cur;
  }
}


