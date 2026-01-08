package io.intellixity.nativa.persistence.mongo.bind;

import org.bson.Document;
import io.intellixity.nativa.persistence.authoring.DiscoveredUserTypeRegistry;
import io.intellixity.nativa.persistence.compile.Bind;
import io.intellixity.nativa.persistence.spi.bind.BindOpKind;
import io.intellixity.nativa.persistence.mongo.bind.MongoBinderProvider.MongoInstantBinder;
import io.intellixity.nativa.persistence.mongo.bind.MongoBinderProvider.MongoUuidBinder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class MongoBinderProviderTest {

  @Test
  void uuid_isStoredAsString() {
    Document d = new Document();
    UUID id = UUID.randomUUID();

    var bind = new Bind(id, "uuid");
    var ctx = new DefaultMongoBindContext(BindOpKind.INSERT, "userId");
    Object encoded = id; // global uuid userType encodes as UUID (identity)

    new MongoUuidBinder().bind(d, ctx, bind, encoded, new DiscoveredUserTypeRegistry("mongo"));
    assertEquals(id.toString(), d.get("userId"));
  }

  @Test
  void listUuid_isStoredAsListOfStrings() {
    Document d = new Document();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();

    var bind = new Bind(List.of(a, b), "list<uuid>");
    var ctx = new DefaultMongoBindContext(BindOpKind.INSERT, "ids");
    Object encoded = List.of(a, b);

    new MongoUuidBinder().bind(d, ctx, bind, encoded, new DiscoveredUserTypeRegistry("mongo"));
    assertEquals(List.of(a.toString(), b.toString()), d.get("ids"));
  }

  @Test
  void instant_isStoredAsDate() {
    Document d = new Document();
    Instant ts = Instant.parse("2025-01-01T00:00:00Z");

    var bind = new Bind(ts, "instant");
    var ctx = new DefaultMongoBindContext(BindOpKind.INSERT, "createdAt");
    Object encoded = ts; // global instant userType encodes as Instant (identity)

    new MongoInstantBinder().bind(d, ctx, bind, encoded, new DiscoveredUserTypeRegistry("mongo"));
    Object v = d.get("createdAt");
    assertTrue(v instanceof java.util.Date);
    assertEquals(java.util.Date.from(ts), v);
  }

  @Test
  void listInstant_isStoredAsListOfDates() {
    Document d = new Document();
    Instant a = Instant.parse("2025-01-01T00:00:00Z");
    Instant b = Instant.parse("2025-01-02T00:00:00Z");

    var bind = new Bind(List.of(a, b), "list<instant>");
    var ctx = new DefaultMongoBindContext(BindOpKind.INSERT, "ts");
    Object encoded = List.of(a, b);

    new MongoInstantBinder().bind(d, ctx, bind, encoded, new DiscoveredUserTypeRegistry("mongo"));
    Object v = d.get("ts");
    assertEquals(List.of(java.util.Date.from(a), java.util.Date.from(b)), v);
  }
}




