package io.intellixity.nativa.persistence.query;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.*;
import io.intellixity.nativa.persistence.query.aggregation.GroupBy;

import java.io.IOException;
import java.util.*;

/** Canonical JSON deserializer for {@link Query}. */
public final class QueryJsonDeserializer extends JsonDeserializer<Query> {
  @Override
  public Query deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    ObjectCodec codec = p.getCodec();
    JsonNode root = codec.readTree(p);
    if (root == null || root.isNull()) return null;
    if (!root.isObject()) throw new IllegalArgumentException("Query JSON must be an object");

    Query q = new Query();

    JsonNode params = root.get("params");
    if (params != null && params.isObject()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> m = codec.treeToValue(params, Map.class);
      q.withParams(m);
    }

    JsonNode filter = root.get("filter");
    if (filter != null && !filter.isNull()) {
      q.withFilter(parseElement(filter, codec));
    }

    JsonNode page = root.get("page");
    if (page != null && page.isObject()) {
      q.withPage(parsePage(page, codec));
    }

    JsonNode sort = root.get("sort");
    if (sort != null && sort.isArray()) {
      List<SortField> fields = new ArrayList<>();
      for (JsonNode s : sort) {
        if (!s.isObject()) continue;
        String f = textOrNull(s.get("field"));
        String dir = textOrNull(s.get("dir"));
        if (f == null) continue;
        SortField.Direction d = (dir == null) ? SortField.Direction.ASC : SortField.Direction.valueOf(dir.toUpperCase());
        fields.add(new SortField(f, d));
      }
      q.withSort(fields);
    }

    JsonNode proj = root.get("projection");
    if (proj != null && proj.isArray()) {
      List<String> out = new ArrayList<>();
      for (JsonNode x : proj) if (x.isTextual()) out.add(x.asText());
      q.withProjection(out);
    }

    JsonNode gb = root.get("groupBy");
    if (gb != null && gb.isObject()) {
      JsonNode f = gb.get("fields");
      if (f != null && f.isArray()) {
        List<String> out = new ArrayList<>();
        for (JsonNode x : f) if (x.isTextual()) out.add(x.asText());
        q.withGroupBy(new GroupBy(out));
      }
    }

    return q;
  }

  private static Page parsePage(JsonNode page, ObjectCodec codec) throws IOException {
    String type = textOrNull(page.get("type"));
    if (type != null && type.equalsIgnoreCase("seek")) {
      int limit = intOrDefault(page.get("limit"), 50);
      Map<String, Object> after = null;
      JsonNode a = page.get("after");
      if (a != null && a.isObject()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = codec.treeToValue(a, Map.class);
        after = m;
      }
      return new SeekPage(limit, after);
    }
    int offset = intOrDefault(page.get("offset"), 0);
    int limit = intOrDefault(page.get("limit"), 50);
    return new OffsetPage(offset, limit);
  }

  private static QueryElement parseElement(JsonNode n, ObjectCodec codec) throws IOException {
    if (n == null || n.isNull()) return null;

    // Canonical group forms: { "and": [ ... ] } / { "or": [ ... ] }
    if (n.isObject() && n.has("and")) {
      return new LogicalGroup(Clause.AND, parseChildren(n.get("and"), codec));
    }
    if (n.isObject() && n.has("or")) {
      return new LogicalGroup(Clause.OR, parseChildren(n.get("or"), codec));
    }

    // Canonical NOT form: { "not": <element> }
    if (n.isObject() && n.has("not")) {
      QueryElement child = parseElement(n.get("not"), codec);
      if (child == null) return null;
      return new NotElement(child);
    }

    // Canonical condition forms: { "eq": { field:..., value:..., not?:... } }
    if (n.isObject()) {
      Iterator<String> it = n.fieldNames();
      while (it.hasNext()) {
        String k = it.next();
        Operator op = tryOp(k);
        if (op == null) continue;
        JsonNode body = n.get(k);
        if (body == null || !body.isObject()) throw new IllegalArgumentException(k + " must be an object");
        return parseCondition(op, body, codec);
      }
    }

    // Back-compat: {clause: AND, elements:[...] } or {operator: EQ, property: tenantId, ...}
    if (n.isObject()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> m = codec.treeToValue(n, Map.class);
      if (m.containsKey("clause") || m.containsKey("elements")) return LogicalGroup.fromMap(m);
      if (m.containsKey("operator") && m.containsKey("property")) return Condition.fromMap(m);

      Object op = m.get("operator");
      if (op == null) op = m.get("op");
      Object prop = m.get("property");
      if (prop == null) prop = m.get("propertyPath");
      if (op != null && prop != null) {
        Map<String, Object> mm = new LinkedHashMap<>(m);
        mm.put("operator", String.valueOf(op));
        mm.put("property", String.valueOf(prop));
        return Condition.fromMap(mm);
      }
    }

    throw new IllegalArgumentException("Unsupported filter element: " + n);
  }

  private static List<QueryElement> parseChildren(JsonNode arr, ObjectCodec codec) throws IOException {
    if (arr == null || !arr.isArray()) return List.of();
    List<QueryElement> out = new ArrayList<>();
    for (JsonNode x : arr) {
      QueryElement e = parseElement(x, codec);
      if (e != null) out.add(e);
    }
    return out;
  }

  private static QueryElement parseCondition(Operator op, JsonNode body, ObjectCodec codec) throws IOException {
    String field = textOrNull(body.get("field"));
    if (field == null) throw new IllegalArgumentException(op + " requires field");
    boolean not = boolOrDefault(body.get("not"), false);

    Object value;
    Object lower;
    Object upper;

    if (op == Operator.RANGE) {
      lower = decodeValue(body.get("lower"), codec);
      upper = decodeValue(body.get("upper"), codec);
      return new Condition(field, op, null, lower, upper, not);
    }

    if (op == Operator.IN || op == Operator.NIN) {
      value = decodeValue(body.get("values"), codec);
      return new Condition(field, op, value, null, null, not);
    }

    value = decodeValue(body.get("value"), codec);
    return new Condition(field, op, value, null, null, not);
  }

  private static Object decodeValue(JsonNode v, ObjectCodec codec) throws IOException {
    if (v == null || v.isNull()) return null;
    // Param: {"param":"x"} or {"$param":"x"}
    if (v.isObject()) {
      JsonNode p = v.get("param");
      if (p == null) p = v.get("$param");
      if (p != null && p.isTextual()) return QueryValues.param(p.asText());
    }
    return codec.treeToValue(v, Object.class);
  }

  private static Operator tryOp(String key) {
    if (key == null) return null;
    try {
      return Operator.valueOf(key.toUpperCase());
    } catch (Exception e) {
      return null;
    }
  }

  private static String textOrNull(JsonNode n) {
    return (n == null || n.isNull()) ? null : n.asText();
  }

  private static int intOrDefault(JsonNode n, int def) {
    if (n == null || n.isNull()) return def;
    return n.isNumber() ? n.intValue() : Integer.parseInt(n.asText());
  }

  private static boolean boolOrDefault(JsonNode n, boolean def) {
    if (n == null || n.isNull()) return def;
    return n.isBoolean() ? n.booleanValue() : Boolean.parseBoolean(n.asText());
  }
}


