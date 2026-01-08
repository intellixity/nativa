package io.intellixity.nativa.persistence.jdbc.dml;

import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.dmlast.DmlPlanner;
import io.intellixity.nativa.persistence.query.Condition;
import io.intellixity.nativa.persistence.query.Operator;
import org.junit.jupiter.api.Test;
import io.intellixity.nativa.persistence.pojo.PojoAccessor;
import io.intellixity.nativa.persistence.pojo.PojoAccessorRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class JdbcDmlPlannerTest {

  record OrderPojo(UUID id, String tenantId, String status) {}

  @Test
  void buildsUpdateByIdUsingMapping() {
    EntityAuthoring ea = new EntityAuthoring(
        "Order",
        AuthoringKind.ENTITY,
        "orders",
        "com.acme.Order",
        true,
        Map.of(
            "id", new FieldDef(new ScalarTypeRef("uuid", Map.of()), false, true),
            "tenantId", new FieldDef(new ScalarTypeRef("string", Map.of()), false, false),
            "status", new FieldDef(new ScalarTypeRef("string", Map.of()), false, false)
        ),
        Map.of()
    );

    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put("id", "id");
    mapping.put("tenantId", "tenant_id");
    mapping.put("status", "status");

    ViewDef view = new ViewDef("order_view", mapping, null);

    AuthoringRegistry reg = new AuthoringRegistry() {
      @Override public EntityAuthoring getEntityAuthoring(String authoringId) { throw new UnsupportedOperationException(); }
      @Override public ViewDef getViewDef(String viewDefId) { throw new UnsupportedOperationException(); }
    };

    PojoAccessorRegistry accessors = new PojoAccessorRegistry() {
      @Override
      public PojoAccessor<?> accessorFor(String authoringId) {
        if (!"Order".equals(authoringId)) throw new IllegalArgumentException("Unknown type: " + authoringId);
        return (PojoAccessor<OrderPojo>) (pojo, path) -> switch (path) {
          case "id" -> pojo.id();
          case "tenantId" -> pojo.tenantId();
          case "status" -> pojo.status();
          default -> null;
        };
      }
    };

    DmlPlanner p = new JdbcDmlPlanner(reg, accessors, java.util.Set.of("tenantId"));
    UUID id = UUID.randomUUID();
    OrderPojo pojo = new OrderPojo(id, "t1", "CREATED");

    var upd = p.planUpdateById(ea, view, pojo);
    assertEquals("orders", upd.table());
    assertEquals(2, upd.sets().size());
    assertTrue(upd.sets().stream().anyMatch(x -> "tenant_id".equals(x.column())));
    assertTrue(upd.sets().stream().anyMatch(x -> "status".equals(x.column())));

    assertTrue(upd.where() instanceof Condition);
    var wc = (Condition) upd.where();
    assertEquals("id", wc.property());
    assertEquals(Operator.EQ, wc.operator());
    assertEquals(id, wc.value());
  }
}


