package io.intellixity.nativa.persistence.spi.exec;

import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.compile.PropertyTypeResolver;
import io.intellixity.nativa.persistence.query.Query;
import io.intellixity.nativa.persistence.query.QueryElement;
import io.intellixity.nativa.persistence.query.QueryFilters;
import io.intellixity.nativa.persistence.query.QueryValidationException;
import io.intellixity.nativa.persistence.query.SortField;
import io.intellixity.nativa.persistence.query.aggregation.GroupBy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class DefaultQueryValidationStrategyTest {
  private static EntityAuthoring orderEntity() {
    return new EntityAuthoring(
        "Order",
        AuthoringKind.ENTITY,
        "orders",
        "com.acme.Order",
        true,
        Map.of(
            "paymentStatus", new FieldDef(new ScalarTypeRef("string", Map.of()), false, false)
        ),
        Map.of()
    );
  }

  private static PropertyTypeResolver typesFor(EntityAuthoring ea) {
    return new PropertyTypeResolver(new AuthoringRegistry() {
      @Override public EntityAuthoring getEntityAuthoring(String authoringId) { return ea; }
      @Override public ViewDef getViewDef(String viewDefId) { throw new UnsupportedOperationException(); }
    });
  }

  @Test
  void throwsOnUnknownFilterProperty() {
    EntityAuthoring ea = orderEntity();
    PropertyTypeResolver types = typesFor(ea);
    ViewDef view = new ViewDef("order_view", Map.of(), null);
    Query q = Query.of(QueryFilters.eq("status", "CREATED"));
    QueryElement normalized = q.filter();

    DefaultQueryValidationStrategy v = new DefaultQueryValidationStrategy();
    QueryValidationException ex = assertThrows(
        QueryValidationException.class,
        () -> v.validate(ea, view, q, normalized, types)
    );
    assertTrue(ex.getMessage().contains("Unknown scalar field path 'status'"));
  }

  @Test
  void throwsOnUnknownSortField() {
    EntityAuthoring ea = orderEntity();
    PropertyTypeResolver types = typesFor(ea);
    ViewDef view = new ViewDef("order_view", Map.of(), null);
    Query q = new Query().withSort(List.of(new SortField("status", SortField.Direction.ASC)));

    DefaultQueryValidationStrategy v = new DefaultQueryValidationStrategy();
    QueryValidationException ex = assertThrows(
        QueryValidationException.class,
        () -> v.validate(ea, view, q, null, types)
    );
    assertTrue(ex.getMessage().contains("Unknown scalar field path 'status'"));
  }

  @Test
  void throwsOnUnknownGroupByField() {
    EntityAuthoring ea = orderEntity();
    PropertyTypeResolver types = typesFor(ea);
    ViewDef view = new ViewDef("order_view", Map.of(), null);
    Query q = new Query().withGroupBy(GroupBy.of("status"));

    DefaultQueryValidationStrategy v = new DefaultQueryValidationStrategy();
    QueryValidationException ex = assertThrows(
        QueryValidationException.class,
        () -> v.validate(ea, view, q, null, types)
    );
    assertTrue(ex.getMessage().contains("Unknown scalar field path 'status'"));
  }
}


