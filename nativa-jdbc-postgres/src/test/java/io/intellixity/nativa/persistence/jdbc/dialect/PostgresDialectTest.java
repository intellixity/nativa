package io.intellixity.nativa.persistence.jdbc.dialect;

import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.compile.Bind;
import io.intellixity.nativa.persistence.compile.PropertyTypeResolver;
import io.intellixity.nativa.persistence.jdbc.SqlStatement;
import io.intellixity.nativa.persistence.jdbc.postgres.PostgresDialect;
import io.intellixity.nativa.persistence.query.QueryElement;
import io.intellixity.nativa.persistence.query.QueryFilters;
import io.intellixity.nativa.persistence.query.QueryValidationException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class PostgresDialectTest {
  @Test
  void throwsOnUnknownPropertyPath() {
    EntityAuthoring ea = new EntityAuthoring(
        "Order",
        AuthoringKind.ENTITY,
        "orders",
        "com.acme.Order",
        true,
        Map.of(
            "tenantId", new FieldDef(new ScalarTypeRef("string", Map.of()), false, false)
        ),
        Map.of()
    );

    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put("tenantId", "tenant_id");
    ViewDef view = new ViewDef("order_view", mapping, null);

    PostgresDialect d = new PostgresDialect();
    QueryElement filter = QueryFilters.eq("status", "CREATED"); // not in mapping
    PropertyTypeResolver types = new PropertyTypeResolver(new AuthoringRegistry() {
      @Override public EntityAuthoring getEntityAuthoring(String authoringId) { return ea; }
      @Override public ViewDef getViewDef(String viewDefId) { throw new UnsupportedOperationException(); }
    });

    QueryValidationException ex = assertThrows(
        QueryValidationException.class,
        () -> d.mergeSelect(ea, view, filter, List.of(), null, null, types)
    );
    assertTrue(ex.getMessage().contains("Unknown field path 'status'"));
  }

  @Test
  void mergesWhereOnDerivedBaseSql() {
    EntityAuthoring ea = new EntityAuthoring(
        "Order",
        AuthoringKind.ENTITY,
        "orders",
        "com.acme.Order",
        true,
        Map.of(
            "tenantId", new FieldDef(new ScalarTypeRef("string", Map.of()), false, false)
        ),
        Map.of()
    );

    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put("tenantId", "tenant_id");
    ViewDef view = new ViewDef("order_view", mapping, null);

    PostgresDialect d = new PostgresDialect();
    QueryElement filter = QueryFilters.eq("tenantId", "tenant-1");
    PropertyTypeResolver types = new PropertyTypeResolver(new AuthoringRegistry() {
      @Override public EntityAuthoring getEntityAuthoring(String authoringId) { return ea; }
      @Override public ViewDef getViewDef(String viewDefId) { throw new UnsupportedOperationException(); }
    });

    SqlStatement stmt = d.mergeSelect(ea, view, filter, List.of(), null, null, types);
    assertTrue(stmt.sql().contains("FROM \"orders\""));
    assertTrue(stmt.sql().contains("WHERE tenant_id = :b1"));
    assertEquals(1, stmt.binds().size());
    Bind b = stmt.binds().getFirst();
    assertEquals("tenant-1", b.value());
    assertEquals("string", b.userTypeId());
  }

  @Test
  void rendersArrayContains() {
    EntityAuthoring ea = new EntityAuthoring(
        "Order",
        AuthoringKind.ENTITY,
        "orders",
        "com.acme.Order",
        true,
        Map.of(
            "tags", new FieldDef(new ScalarTypeRef("list<string>", Map.of()), false, false)
        ),
        Map.of()
    );

    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put("tags", "tags");
    ViewDef view = new ViewDef("order_view", mapping, null);

    PostgresDialect d = new PostgresDialect();
    QueryElement filter = QueryFilters.arrayContains("tags", List.of("a", "b"));
    PropertyTypeResolver types = new PropertyTypeResolver(new AuthoringRegistry() {
      @Override public EntityAuthoring getEntityAuthoring(String authoringId) { return ea; }
      @Override public ViewDef getViewDef(String viewDefId) { throw new UnsupportedOperationException(); }
    });

    SqlStatement stmt = d.mergeSelect(ea, view, filter, List.of(), null, null, types);
    assertTrue(stmt.sql().contains("tags @> :b1"));
    assertEquals(1, stmt.binds().size());
    Bind b = stmt.binds().getFirst();
    assertEquals(List.of("a", "b"), b.value());
    assertEquals("list<string>", b.userTypeId());
  }

  @Test
  void rendersArrayOverlaps() {
    EntityAuthoring ea = new EntityAuthoring(
        "Order",
        AuthoringKind.ENTITY,
        "orders",
        "com.acme.Order",
        true,
        Map.of(
            "tags", new FieldDef(new ScalarTypeRef("list<string>", Map.of()), false, false)
        ),
        Map.of()
    );

    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put("tags", "tags");
    ViewDef view = new ViewDef("order_view", mapping, null);

    PostgresDialect d = new PostgresDialect();
    QueryElement filter = QueryFilters.arrayOverlaps("tags", List.of("a", "b"));
    PropertyTypeResolver types = new PropertyTypeResolver(new AuthoringRegistry() {
      @Override public EntityAuthoring getEntityAuthoring(String authoringId) { return ea; }
      @Override public ViewDef getViewDef(String viewDefId) { throw new UnsupportedOperationException(); }
    });

    SqlStatement stmt = d.mergeSelect(ea, view, filter, List.of(), null, null, types);
    assertTrue(stmt.sql().contains("tags && :b1"));
    assertEquals(1, stmt.binds().size());
    Bind b = stmt.binds().getFirst();
    assertEquals(List.of("a", "b"), b.value());
    assertEquals("list<string>", b.userTypeId());
  }

  @Test
  void rendersJsonPathExists() {
    EntityAuthoring ea = new EntityAuthoring(
        "Order",
        AuthoringKind.ENTITY,
        "orders",
        "com.acme.Order",
        true,
        Map.of(
            "meta", new FieldDef(new ScalarTypeRef("json", Map.of()), true, false)
        ),
        Map.of()
    );

    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put("meta", "meta");
    ViewDef view = new ViewDef("order_view", mapping, null);

    PostgresDialect d = new PostgresDialect();
    QueryElement filter = QueryFilters.jsonPathExists("meta", "$.a.b");
    PropertyTypeResolver types = new PropertyTypeResolver(new AuthoringRegistry() {
      @Override public EntityAuthoring getEntityAuthoring(String authoringId) { return ea; }
      @Override public ViewDef getViewDef(String viewDefId) { throw new UnsupportedOperationException(); }
    });

    SqlStatement stmt = d.mergeSelect(ea, view, filter, List.of(), null, null, types);
    assertTrue(stmt.sql().contains("meta #> '{a,b}' IS NOT NULL"));
  }

  @Test
  void rendersJsonValueEqAsContainmentFragment() {
    EntityAuthoring ea = new EntityAuthoring(
        "Order",
        AuthoringKind.ENTITY,
        "orders",
        "com.acme.Order",
        true,
        Map.of(
            "meta", new FieldDef(new ScalarTypeRef("json", Map.of()), true, false)
        ),
        Map.of()
    );

    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put("meta", "meta");
    ViewDef view = new ViewDef("order_view", mapping, null);

    PostgresDialect d = new PostgresDialect();
    QueryElement filter = QueryFilters.jsonValueEq("meta", "$.a.b", 5);
    PropertyTypeResolver types = new PropertyTypeResolver(new AuthoringRegistry() {
      @Override public EntityAuthoring getEntityAuthoring(String authoringId) { return ea; }
      @Override public ViewDef getViewDef(String viewDefId) { throw new UnsupportedOperationException(); }
    });

    SqlStatement stmt = d.mergeSelect(ea, view, filter, List.of(), null, null, types);
    assertTrue(stmt.sql().contains("meta @> :b1"));
    assertEquals(1, stmt.binds().size());
    Bind b = stmt.binds().getFirst();
    assertEquals("json", b.userTypeId());
    assertEquals(Map.of("a", Map.of("b", 5)), b.value());
  }
}


