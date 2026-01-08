package io.intellixity.nativa.persistence.mongo;

import org.bson.Document;
import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.compile.PropertyTypeResolver;
import io.intellixity.nativa.persistence.query.QueryFilters;
import io.intellixity.nativa.persistence.query.QueryValidationException;
import io.intellixity.nativa.persistence.query.SortField;
import io.intellixity.nativa.persistence.spi.bind.BindOpKind;
import io.intellixity.nativa.persistence.spi.bind.DiscoveredBinderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class MongoQueryMappingTest {
  @Test
  void filter_defaultPath_isCamelCase() {
    EntityAuthoring ea = entityWithFirstName();
    ViewDef view = new ViewDef("customer_view", Map.of(), null);

    PropertyTypeResolver types = typesFor(ea);
    var userTypes = new DiscoveredUserTypeRegistry("mongo");
    var binders = new DiscoveredBinderRegistry("mongo");

    Document d = MongoQueryRenderer.toBson(
        ea, view,
        QueryFilters.eq("firstName", "A"),
        types, userTypes, binders, BindOpKind.FILTER
    );

    assertEquals("A", d.get("firstName"));
    assertFalse(d.containsKey("first_name"));
  }

  @Test
  void filter_explicitMapping_overridesPath() {
    EntityAuthoring ea = entityWithFirstName();
    ViewDef view = new ViewDef("customer_view", Map.of("firstName", "first_name"), null);

    PropertyTypeResolver types = typesFor(ea);
    var userTypes = new DiscoveredUserTypeRegistry("mongo");
    var binders = new DiscoveredBinderRegistry("mongo");

    Document d = MongoQueryRenderer.toBson(
        ea, view,
        QueryFilters.eq("firstName", "A"),
        types, userTypes, binders, BindOpKind.FILTER
    );

    assertEquals("A", d.get("first_name"));
    assertFalse(d.containsKey("firstName"));
  }

  @Test
  void sort_defaultPath_isCamelCase() {
    EntityAuthoring ea = entityWithFirstName();
    ViewDef view = new ViewDef("customer_view", Map.of(), null);

    MongoDialect dialect = new MongoDialect();
    MongoStatement st = dialect.mergeSelect(
        ea, view,
        null,
        List.of(new SortField("firstName", SortField.Direction.DESC)),
        null, null,
        typesFor(ea)
    );

    assertNotNull(st.sort());
    assertEquals(-1, st.sort().get("firstName"));
    assertFalse(st.sort().containsKey("first_name"));
  }

  @Test
  void sort_explicitMapping_overridesPath() {
    EntityAuthoring ea = entityWithFirstName();
    ViewDef view = new ViewDef("customer_view", Map.of("firstName", "first_name"), null);

    MongoDialect dialect = new MongoDialect();
    MongoStatement st = dialect.mergeSelect(
        ea, view,
        null,
        List.of(new SortField("firstName", SortField.Direction.DESC)),
        null, null,
        typesFor(ea)
    );

    assertNotNull(st.sort());
    assertEquals(-1, st.sort().get("first_name"));
    assertFalse(st.sort().containsKey("firstName"));
  }

  @Test
  void unknownField_throwsQueryValidationException() {
    EntityAuthoring ea = entityWithFirstName();
    ViewDef view = new ViewDef("customer_view", Map.of(), null);

    PropertyTypeResolver types = typesFor(ea);
    var userTypes = new DiscoveredUserTypeRegistry("mongo");
    var binders = new DiscoveredBinderRegistry("mongo");

    QueryValidationException ex = assertThrows(
        QueryValidationException.class,
        () -> MongoQueryRenderer.toBson(
            ea, view,
            QueryFilters.eq("status", "CREATED"),
            types, userTypes, binders, BindOpKind.FILTER
        )
    );
    assertTrue(ex.getMessage().contains("Unknown scalar field path 'status'"));
  }

  private static EntityAuthoring entityWithFirstName() {
    return new EntityAuthoring(
        "Customer",
        AuthoringKind.ENTITY,
        "customers",
        "com.acme.Customer",
        true,
        Map.of(
            "firstName", new FieldDef(new ScalarTypeRef("string", Map.of()), false, false)
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
}


