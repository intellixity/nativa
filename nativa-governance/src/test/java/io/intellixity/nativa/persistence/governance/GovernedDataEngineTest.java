package io.intellixity.nativa.persistence.governance;

import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.exec.EntityViewRef;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.exec.handle.EngineHandle;
import io.intellixity.nativa.persistence.pojo.Nulls;
import io.intellixity.nativa.persistence.pojo.PojoMutator;
import io.intellixity.nativa.persistence.pojo.PojoMutatorRegistry;
import io.intellixity.nativa.persistence.query.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class GovernedDataEngineTest {

  static final class TestPojo implements Nulls {
    private String tenantId;
    private String userId;
    private final Set<String> nulls = new HashSet<>();
    public String tenantId() { return tenantId; }
    public String userId() { return userId; }
    public TestPojo tenantId(String v) { this.tenantId = v; return this; }
    public TestPojo userId(String v) { this.userId = v; return this; }
    @Override public Collection<String> getNulls() { return nulls; }
  }

  static final class CapturingEngine implements DataEngine<EngineHandle<?>> {
    Query lastQuery;
    Object lastEntity;
    private final boolean multiTenant;
    final EngineHandle<?> handle = new EngineHandle<>() {
      @Override public String id() { return "cap"; }
      @Override public Object client() { return new Object(); }
      @Override public String namespace() { return "ns"; }
      @Override public boolean multiTenant() { return multiTenant; }
    };

    CapturingEngine() {
      this(true);
    }

    CapturingEngine(boolean multiTenant) {
      this.multiTenant = multiTenant;
    }

    @Override public EngineHandle<?> handle() { return handle; }
    @Override public Propagation defaultPropagation() { return Propagation.REQUIRED; }
    @Override public <T> T inTx(Propagation propagation, Supplier<T> work) { return work.get(); }
    @Override public <T> T inTx(Supplier<T> work) { return work.get(); }
    @Override public <T> List<T> select(EntityViewRef ref, Query query) { this.lastQuery = query; return List.of(); }
    @Override public long count(EntityViewRef ref, Query query) { this.lastQuery = query; return 0; }
    @Override public <T> T insert(EntityViewRef ref, T entity) { this.lastEntity = entity; return entity; }
    @Override public <T> void bulkInsert(EntityViewRef ref, List<T> entities) { throw new UnsupportedOperationException(); }
    @Override public <T> T upsert(EntityViewRef ref, T entity) { this.lastEntity = entity; return entity; }
    @Override public <T> void bulkUpsert(EntityViewRef ref, List<T> entities) { throw new UnsupportedOperationException(); }
    @Override public <T> long update(EntityViewRef ref, T entity) { this.lastEntity = entity; return 1; }
    @Override public <T> long bulkUpdate(EntityViewRef ref, List<T> entities) { throw new UnsupportedOperationException(); }
    @Override public <T> long updateByCriteria(EntityViewRef ref, Query query, T entity) { this.lastQuery = query; this.lastEntity = entity; return 1; }
    @Override public long deleteByCriteria(EntityViewRef ref, Query query) { this.lastQuery = query; return 1; }
  }

  private static AuthoringRegistry authoringWithGovernance() {
    EntityAuthoring ea = new EntityAuthoring(
        "Order",
        AuthoringKind.ENTITY,
        "orders",
        "com.acme.Order",
        false,
        Map.of(
            "tenantId", new FieldDef(new ScalarTypeRef("string", Map.of()), false, false, false, Map.of("governanceKey", "tenantId")),
            "userId", new FieldDef(new ScalarTypeRef("string", Map.of()), false, false, false, Map.of("governanceKey", "userId"))
        ),
        Map.of()
    );
    return new AuthoringRegistry() {
      @Override public EntityAuthoring getEntityAuthoring(String authoringId) {
        if ("Order".equals(authoringId)) return ea;
        throw new IllegalArgumentException("Unknown authoring type: " + authoringId);
      }

      @Override public ViewDef getViewDef(String viewDefId) {
        throw new UnsupportedOperationException("Not needed for this test: " + viewDefId);
      }
    };
  }

  private static AuthoringRegistry authoringWithTenantAndDealerGovernance() {
    EntityAuthoring ea = new EntityAuthoring(
        "Order",
        AuthoringKind.ENTITY,
        "orders",
        "com.acme.Order",
        false,
        Map.of(
            "tenantId", new FieldDef(new ScalarTypeRef("string", Map.of()), false, false, false, Map.of("governanceKey", "tenantId")),
            "dealerId", new FieldDef(new ScalarTypeRef("string", Map.of()), false, false, false, Map.of("governanceKey", "dealerId"))
        ),
        Map.of()
    );
    return new AuthoringRegistry() {
      @Override public EntityAuthoring getEntityAuthoring(String authoringId) {
        if ("Order".equals(authoringId)) return ea;
        throw new IllegalArgumentException("Unknown authoring type: " + authoringId);
      }

      @Override public ViewDef getViewDef(String viewDefId) {
        throw new UnsupportedOperationException("Not needed for this test: " + viewDefId);
      }
    };
  }

  private static PojoMutatorRegistry mutators() {
    return new PojoMutatorRegistry() {
      @Override public PojoMutator<?> mutatorFor(String authoringId) {
        return (PojoMutator<TestPojo>) (pojo, field, value) -> {
          if (pojo == null) return;
          if ("tenantId".equals(field)) pojo.tenantId(String.valueOf(value));
          else if ("userId".equals(field)) pojo.userId(String.valueOf(value));
        };
      }
    };
  }

  @Test
  void select_injectsGovernanceFilters() {
    AuthoringRegistry reg = authoringWithGovernance();
    CapturingEngine base = new CapturingEngine();
    DataEngine<?> g = new GovernedDataEngine<>(reg, base, mutators());

    EntityViewRef ref = new EntityViewRef("Order", "order_view");
    Query q = Query.and(QueryFilters.eq("tenantId", "t0"));

    Governance.inContext(GovernanceContext.of(Map.of("tenantId", "t1", "userId", "u1")), () -> {
      g.select(ref, q);
      return null;
    });

    assertNotNull(base.lastQuery);
    assertTrue(base.lastQuery.filter() instanceof LogicalGroup);
    LogicalGroup and = (LogicalGroup) base.lastQuery.filter();
    assertEquals(Clause.AND, and.clause());
    assertTrue(and.elements().size() >= 2); // original + governance filters
  }

  @Test
  void select_multiTenant_injectsAndFiltersForPresentTenantKeys() {
    AuthoringRegistry reg = authoringWithTenantAndDealerGovernance();
    CapturingEngine base = new CapturingEngine();
    DataEngine<?> g = new GovernedDataEngine<>(reg, base, mutators());

    EntityViewRef ref = new EntityViewRef("Order", "order_view");
    Query q = new Query();

    GovernanceContext ctx = new GovernanceContext() {
      private final Map<String, Object> m = Map.of("tenantId", "t1", "dealerId", "d1");
      @Override public Object get(String key) { return m.get(key); }
      @Override public String cacheKey() { return "t:t1|d:d1"; }
      @Override public Set<String> tenantKeys() { return Set.of("tenantId", "dealerId"); }
    };

    Governance.inContext(ctx, () -> {
      g.select(ref, q);
      return null;
    });

    assertNotNull(base.lastQuery);
    assertTrue(base.lastQuery.filter() instanceof LogicalGroup);
    LogicalGroup and = (LogicalGroup) base.lastQuery.filter();
    assertEquals(Clause.AND, and.clause());
    assertTrue(and.elements().size() >= 2);
  }

  @Test
  void select_multiTenant_onlyOneTenantKeyPresent_injectsOnlyThatKey() {
    AuthoringRegistry reg = authoringWithTenantAndDealerGovernance();
    CapturingEngine base = new CapturingEngine();
    DataEngine<?> g = new GovernedDataEngine<>(reg, base, mutators());

    EntityViewRef ref = new EntityViewRef("Order", "order_view");

    // When ctx.tenantKeys() declares both keys, both are required.
    // Provide only tenantId to confirm it fails fast.
    GovernanceContext ctx = new GovernanceContext() {
      private final Map<String, Object> m = Map.of("tenantId", "t1");
      @Override public Object get(String key) { return m.get(key); }
      @Override public String cacheKey() { return "t:t1"; }
      @Override public Set<String> tenantKeys() { return Set.of("tenantId", "dealerId"); }
    };

    assertThrows(IllegalStateException.class, () ->
        Governance.inContext(ctx, () -> {
          g.select(ref, new Query());
          return null;
        })
    );

    assertNull(base.lastQuery);
  }

  @Test
  void select_isolatedStore_skipsTenantBoundaryFilters_butStillInjectsOtherGovernance() {
    AuthoringRegistry reg = authoringWithGovernance(); // tenantId + userId
    CapturingEngine base = new CapturingEngine(false); // isolated store
    DataEngine<?> g = new GovernedDataEngine<>(reg, base, mutators());

    EntityViewRef ref = new EntityViewRef("Order", "order_view");

    Governance.inContext(GovernanceContext.of(Map.of("tenantId", "t1", "userId", "u1")), () -> {
      g.select(ref, new Query());
      return null;
    });

    assertNotNull(base.lastQuery);
    Set<String> props = collectProperties(base.lastQuery.filter());
    assertTrue(props.contains("userId"));
    assertFalse(props.contains("tenantId"));
  }

  @Test
  void insert_populatesGovernanceFields() {
    AuthoringRegistry reg = authoringWithGovernance();
    CapturingEngine base = new CapturingEngine();
    DataEngine<?> g = new GovernedDataEngine<>(reg, base, mutators());

    TestPojo pojo = new TestPojo();
    EntityViewRef ref = new EntityViewRef("Order", "order_view");

    Governance.inContext(GovernanceContext.of(Map.of("tenantId", "t1", "userId", "u1")), () -> {
      g.insert(ref, pojo);
      return null;
    });

    assertEquals("t1", pojo.tenantId());
    assertEquals("u1", pojo.userId());
  }

  @Test
  void insert_isolatedStore_populatesTenantFieldIfPresent_andSkipsMissingNonTenantKey() {
    AuthoringRegistry reg = authoringWithGovernance();
    CapturingEngine base = new CapturingEngine(false);
    DataEngine<?> g = new GovernedDataEngine<>(reg, base, mutators());

    TestPojo pojo = new TestPojo();
    EntityViewRef ref = new EntityViewRef("Order", "order_view");

    Governance.inContext(GovernanceContext.of(Map.of("tenantId", "t1")), () -> {
      g.insert(ref, pojo);
      return null;
    });

    assertEquals("t1", pojo.tenantId());
    assertNull(pojo.userId());
  }

  @Test
  void missingTenantBoundaryKey_fails() {
    AuthoringRegistry reg = authoringWithGovernance();
    DataEngine<?> g = new GovernedDataEngine<>(reg, new CapturingEngine(), mutators());
    EntityViewRef ref = new EntityViewRef("Order", "order_view");

    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
        Governance.inContext(GovernanceContext.of(Map.of("userId", "u1")), () -> {
          g.count(ref, new Query());
          return null;
        })
    );
    assertTrue(ex.getMessage().contains("Missing tenant boundary keys"));
  }

  @Test
  void explicitNullOnGovernanceField_fails() {
    AuthoringRegistry reg = authoringWithGovernance();
    DataEngine<?> g = new GovernedDataEngine<>(reg, new CapturingEngine(), mutators());
    EntityViewRef ref = new EntityViewRef("Order", "order_view");

    TestPojo pojo = new TestPojo();
    pojo.getNulls().add("tenantId");

    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
        Governance.inContext(GovernanceContext.of(Map.of("tenantId", "t1", "userId", "u1")), () -> {
          g.insert(ref, pojo);
          return null;
        })
    );
    assertTrue(ex.getMessage().contains("cannot be explicitly set to NULL"));
  }

  private static Set<String> collectProperties(QueryElement el) {
    if (el == null) return Set.of();
    Set<String> out = new LinkedHashSet<>();
    collect(el, out);
    return out;
  }

  private static void collect(QueryElement el, Set<String> out) {
    if (el instanceof Condition c) {
      out.add(c.property());
      return;
    }
    if (el instanceof LogicalGroup g) {
      for (QueryElement child : g.elements()) collect(child, out);
      return;
    }
    if (el instanceof NotElement n) {
      collect(n.element(), out);
    }
  }
}


