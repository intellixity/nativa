package io.intellixity.nativa.persistence.query;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.intellixity.nativa.persistence.query.aggregation.GroupBy;

import java.io.IOException;
import java.util.Map;

/** Canonical JSON serializer for {@link Query}. */
public final class QueryJsonSerializer extends JsonSerializer<Query> {
  @Override
  public void serialize(Query q, JsonGenerator g, SerializerProvider serializers) throws IOException {
    if (q == null) {
      g.writeNull();
      return;
    }

    g.writeStartObject();

    if (q.filter() != null) {
      g.writeFieldName("filter");
      writeElement(q.filter(), g, serializers);
    }

    if (q.page() != null) {
      g.writeObjectField("page", pageToJson(q.page()));
    }

    if (q.sort() != null && !q.sort().isEmpty()) {
      g.writeArrayFieldStart("sort");
      for (SortField sf : q.sort()) {
        g.writeStartObject();
        g.writeStringField("field", sf.field());
        g.writeStringField("dir", sf.direction().name());
        g.writeEndObject();
      }
      g.writeEndArray();
    }

    if (q.projection() != null && !q.projection().isEmpty()) {
      g.writeObjectField("projection", q.projection());
    }

    GroupBy gb = q.groupBy();
    if (gb != null && gb.fields() != null && !gb.fields().isEmpty()) {
      g.writeStartObject("groupBy");
      g.writeObjectField("fields", gb.fields());
      g.writeEndObject();
    }

    if (q.params() != null && !q.params().isEmpty()) {
      g.writeObjectField("params", q.params());
    }

    g.writeEndObject();
  }

  private static Object pageToJson(Page p) {
    if (p instanceof OffsetPage op) {
      return Map.of("offset", op.offset(), "limit", op.limit(), "type", "offset");
    }
    if (p instanceof SeekPage sp) {
      if (sp.after() == null || sp.after().isEmpty()) {
        return Map.of("limit", sp.limit(), "type", "seek");
      }
      return Map.of("limit", sp.limit(), "type", "seek", "after", sp.after());
    }
    return Map.of("limit", p.limit());
  }

  private static void writeElement(QueryElement el, JsonGenerator g, SerializerProvider serializers) throws IOException {
    if (el == null) {
      g.writeNull();
      return;
    }

    if (el instanceof LogicalGroup lg) {
      String key = lg.clause() == Clause.OR ? "or" : "and";
      g.writeStartObject();
      g.writeArrayFieldStart(key);
      for (QueryElement child : lg.elements()) {
        writeElement(child, g, serializers);
      }
      g.writeEndArray();
      g.writeEndObject();
      return;
    }

    if (el instanceof NotElement n) {
      g.writeStartObject();
      g.writeFieldName("not");
      writeElement(n.element(), g, serializers);
      g.writeEndObject();
      return;
    }

    if (el instanceof Condition c) {
      String opKey = c.operator().name().toLowerCase();
      g.writeStartObject();
      g.writeObjectFieldStart(opKey);
      g.writeStringField("field", c.property());
      if (c.not()) g.writeBooleanField("not", true);
      if (c.operator() == Operator.RANGE) {
        g.writeFieldName("lower");
        writeValue(c.lower(), g, serializers);
        g.writeFieldName("upper");
        writeValue(c.upper(), g, serializers);
      } else if (c.operator() == Operator.IN || c.operator() == Operator.NIN) {
        g.writeFieldName("values");
        serializers.defaultSerializeValue(c.value(), g);
      } else {
        g.writeFieldName("value");
        writeValue(c.value(), g, serializers);
      }
      g.writeEndObject();
      g.writeEndObject();
      return;
    }

    serializers.defaultSerializeValue(el, g);
  }

  private static void writeValue(Object v, JsonGenerator g, SerializerProvider serializers) throws IOException {
    if (v instanceof QueryValues.Param p) {
      g.writeStartObject();
      g.writeStringField("param", p.name());
      g.writeEndObject();
      return;
    }
    serializers.defaultSerializeValue(v, g);
  }
}



