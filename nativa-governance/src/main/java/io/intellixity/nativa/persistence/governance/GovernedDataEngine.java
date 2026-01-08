package io.intellixity.nativa.persistence.governance;

import io.intellixity.nativa.persistence.authoring.AuthoringRegistry;
import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.FieldDef;
import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.exec.EntityViewRef;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.exec.handle.EngineHandle;
import io.intellixity.nativa.persistence.pojo.Nulls;
import io.intellixity.nativa.persistence.pojo.PojoMutator;
import io.intellixity.nativa.persistence.pojo.PojoMutatorRegistry;
import io.intellixity.nativa.persistence.query.Query;
import io.intellixity.nativa.persistence.query.QueryElement;
import io.intellixity.nativa.persistence.query.QueryFilters;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Governance wrapper over a {@link DataEngine}.\n
 *
 * Enforces:\n
 * - filter injection based on {@code FieldDef.attrs.governanceKey}\n
 * - population of governance-mapped fields on insert/upsert/update payloads\n
 */
public final class GovernedDataEngine<H extends EngineHandle<?>> implements DataEngine<H> {
  private final AuthoringRegistry authoring;
  private final DataEngine<H> delegate;
  private final PojoMutatorRegistry mutators;
  /**
   * Context-key names that define the tenant boundary (e.g. {@code tenantId}, {@code orgId}, {@code x}, {@code y}).
   * <p>
   * If the underlying {@link EngineHandle#multiTenant()} is true (shared store), at least one of these keys must be
   * present in the {@link GovernanceContext} for every operation, and every present key contributes an AND filter.
   * If {@link EngineHandle#multiTenant()} is false (isolated store), tenant-boundary filters are skipped.
   */
  private final Set<String> tenantBoundaryKeys;
  private final Map<String, GovernanceSpec> specCache = new ConcurrentHashMap<>();

  private record GovernanceSpec(
      Map<String, List<String>> fieldToKeys,
      Map<String, Set<String>> keyToFields
  ) {
    boolean isEmpty() {
      return fieldToKeys == null || fieldToKeys.isEmpty();
    }

    boolean hasGovernanceKey(String key) {
      if (key == null) return false;
      return keyToFields.containsKey(key);
    }
  }

  private record TenantBoundary(boolean sharedStore, Set<String> tenantKeys) {
    boolean isTenantKey(String key) {
      return key != null && tenantKeys != null && tenantKeys.contains(key);
    }
  }

  public GovernedDataEngine(AuthoringRegistry authoring, DataEngine<H> delegate, PojoMutatorRegistry mutators) {
    this(authoring, delegate, mutators, Set.of("tenantId"));
  }

  public GovernedDataEngine(AuthoringRegistry authoring,
                            DataEngine<H> delegate,
                            PojoMutatorRegistry mutators,
                            Set<String> tenantBoundaryKeys) {
    this.authoring = Objects.requireNonNull(authoring, "authoring");
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.mutators = Objects.requireNonNull(mutators, "mutators");
    this.tenantBoundaryKeys = normalizeKeySet(tenantBoundaryKeys);
  }

  @Override
  public H handle() {
    return delegate.handle();
  }

  @Override
  public Propagation defaultPropagation() {
    return delegate.defaultPropagation();
  }

  @Override
  public <T> T inTx(Propagation propagation, Supplier<T> work) {
    return delegate.inTx(propagation, work);
  }

  @Override
  public <T> List<T> select(EntityViewRef ref, Query query) {
    return delegate.select(ref, withGovernanceFilters(ref, query));
  }

  @Override
  public long count(EntityViewRef ref, Query query) {
    return delegate.count(ref, withGovernanceFilters(ref, query));
  }

  @Override
  public <T> T insert(EntityViewRef ref, T entity) {
    applyGovernanceValues(ref, entity);
    return delegate.insert(ref, entity);
  }

  @Override
  public <T> void bulkInsert(EntityViewRef ref, List<T> entities) {
    if (entities != null) for (T e : entities) applyGovernanceValues(ref, e);
    delegate.bulkInsert(ref, entities);
  }

  @Override
  public <T> T upsert(EntityViewRef ref, T entity) {
    applyGovernanceValues(ref, entity);
    return delegate.upsert(ref, entity);
  }

  @Override
  public <T> void bulkUpsert(EntityViewRef ref, List<T> entities) {
    if (entities != null) for (T e : entities) applyGovernanceValues(ref, e);
    delegate.bulkUpsert(ref, entities);
  }

  @Override
  public <T> long update(EntityViewRef ref, T entity) {
    applyGovernanceValues(ref, entity);
    return delegate.update(ref, entity);
  }

  @Override
  public <T> long bulkUpdate(EntityViewRef ref, List<T> entities) {
    if (entities != null) for (T e : entities) applyGovernanceValues(ref, e);
    return delegate.bulkUpdate(ref, entities);
  }

  @Override
  public <T> long updateByCriteria(EntityViewRef ref, Query query, T entity) {
    applyGovernanceValues(ref, entity);
    return delegate.updateByCriteria(ref, withGovernanceFilters(ref, query), entity);
  }

  @Override
  public long deleteByCriteria(EntityViewRef ref, Query query) {
    return delegate.deleteByCriteria(ref, withGovernanceFilters(ref, query));
  }

  private Query withGovernanceFilters(EntityViewRef ref, Query query) {
    GovernanceContext ctx = Governance.currentOrThrow();
    EntityAuthoring ea = authoring.getEntityAuthoring(ref.type());
    GovernanceSpec spec = specFor(ea);
    if (spec.isEmpty()) return query;

    TenantBoundary boundary = tenantBoundary(ctx, spec);

    List<QueryElement> conds = new ArrayList<>();
    for (var e : spec.fieldToKeys.entrySet()) {
      String field = e.getKey();
      List<String> keys = e.getValue();
      if (keys == null || keys.isEmpty()) continue;

      for (String key : keys) {
        if (key == null || key.isBlank()) continue;
        // Isolated store: tenant boundary is enforced by DB isolation, so skip tenant filters.
        if (!boundary.sharedStore() && boundary.isTenantKey(key)) continue;
        Object v = ctx.get(key);
        if (v == null) continue; // optional key
        conds.add(QueryFilters.eq(field, v));
      }
    }

    Query effective = (query == null) ? new Query() : query;
    if (effective.filter() != null) conds.addFirst(effective.filter());

    return new Query()
        .withFilter((conds.size() == 1) ? conds.getFirst() : QueryFilters.and(conds.toArray(new QueryElement[0])))
        .withParams(effective.params())
        .withPage(effective.page())
        .withSort(effective.sort())
        .withGroupBy(effective.groupBy());
  }

  private void applyGovernanceValues(EntityViewRef ref, Object entity) {
    if (entity == null) return;
    GovernanceContext ctx = Governance.currentOrThrow();
    EntityAuthoring ea = authoring.getEntityAuthoring(ref.type());
    GovernanceSpec spec = specFor(ea);
    if (spec.isEmpty()) return;
    tenantBoundary(ctx, spec); // validates for shared store

    // prevent explicitly nulling governance fields
    if (entity instanceof Nulls n) {
      Collection<String> nulls = n.getNulls();
      if (nulls != null && !nulls.isEmpty()) {
        for (String field : spec.fieldToKeys.keySet()) {
          List<String> keys = spec.fieldToKeys.get(field);
          if (keys == null || keys.isEmpty()) continue;
          if (!nulls.contains(field)) continue;
          // Only fail if we are actually going to populate this field based on present context keys.
          if (wouldPopulate(keys, ctx)) {
            throw new IllegalStateException("Governance field '" + field + "' cannot be explicitly set to NULL.");
          }
        }
      }
    }

    @SuppressWarnings("unchecked")
    PojoMutator<Object> m = (PojoMutator<Object>) mutators.mutatorFor(ea.type());

    for (var e : spec.fieldToKeys.entrySet()) {
      String field = e.getKey();
      List<String> keys = e.getValue();
      if (keys == null || keys.isEmpty()) continue;

      // Optional population: set only if at least one of the keys exists in the context.
      // Tenant-boundary fields are still populated even in isolated stores (if the field exists and key is present).
      Object v = firstPresent(keys, ctx);
      if (v == null) continue;
      m.set(entity, field, v);
    }
  }

  private GovernanceSpec specFor(EntityAuthoring ea) {
    Objects.requireNonNull(ea, "ea");
    return specCache.computeIfAbsent(ea.type(), _t -> buildSpec(ea));
  }

  private static GovernanceSpec buildSpec(EntityAuthoring ea) {
    Map<String, List<String>> fieldToKeys = new LinkedHashMap<>();
    Map<String, Set<String>> keyToFields = new LinkedHashMap<>();
    for (var e : ea.fields().entrySet()) {
      String field = e.getKey();
      FieldDef fd = e.getValue();
      if (fd == null) continue;
      Object raw = (fd.attrs() == null) ? null : fd.attrs().get("governanceKey");
      if (raw == null) continue;

      List<String> keys = parseGovernanceKeys(raw);
      if (keys.isEmpty()) continue;
      fieldToKeys.put(field, keys);

      for (String k : keys) {
        if (k == null || k.isBlank()) continue;
        keyToFields.computeIfAbsent(k, __ -> new LinkedHashSet<>()).add(field);
      }
    }
    return new GovernanceSpec(
        fieldToKeys.isEmpty() ? Map.of() : Map.copyOf(fieldToKeys),
        keyToFields.isEmpty() ? Map.of() : copySetMap(keyToFields)
    );
  }

  private TenantBoundary tenantBoundary(GovernanceContext ctx, GovernanceSpec spec) {
    boolean sharedStore = delegate.handle().multiTenant();
    Set<String> effectiveKeys = effectiveTenantKeys(ctx);
    if (sharedStore) {
      requireAnyTenantBoundaryKey(ctx, effectiveKeys);
      requireAllTenantBoundaryValues(ctx, effectiveKeys);
      requireTenantKeysAreMappable(spec, effectiveKeys);
    }
    return new TenantBoundary(sharedStore, effectiveKeys);
  }

  private Set<String> effectiveTenantKeys(GovernanceContext ctx) {
    Set<String> fromCtx = (ctx == null) ? null : ctx.tenantKeys();
    if (fromCtx != null && !fromCtx.isEmpty()) return normalizeKeySet(fromCtx);
    return tenantBoundaryKeys;
  }

  private static void requireAnyTenantBoundaryKey(GovernanceContext ctx, Set<String> tenantKeys) {
    if (tenantKeys == null || tenantKeys.isEmpty()) return;
    for (String k : tenantKeys) {
      if (k == null || k.isBlank()) continue;
      if (ctx.get(k) != null) return;
    }
    throw new IllegalStateException("Missing tenant boundary keys in GovernanceContext. Require at least one of: " + tenantKeys);
  }

  private static void requireAllTenantBoundaryValues(GovernanceContext ctx, Set<String> tenantKeys) {
    if (tenantKeys == null || tenantKeys.isEmpty()) return;
    for (String k : tenantKeys) {
      if (k == null || k.isBlank()) continue;
      if (ctx.get(k) == null) {
        throw new IllegalStateException("Missing required tenant boundary key in GovernanceContext: " + k);
      }
    }
  }

  private static void requireTenantKeysAreMappable(GovernanceSpec spec, Set<String> tenantKeys) {
    if (tenantKeys == null || tenantKeys.isEmpty()) return;
    for (String k : tenantKeys) {
      if (k == null || k.isBlank()) continue;
      if (!spec.hasGovernanceKey(k)) {
        throw new IllegalStateException("Tenant boundary key '" + k + "' is not mapped by any field (attrs.governanceKey) for entity.");
      }
    }
  }

  private static Object firstPresent(List<String> keys, GovernanceContext ctx) {
    for (String k : keys) {
      if (k == null || k.isBlank()) continue;
      Object v = ctx.get(k);
      if (v != null) return v;
    }
    return null;
  }

  private static boolean wouldPopulate(List<String> keys, GovernanceContext ctx) {
    return firstPresent(keys, ctx) != null;
  }

  private static Set<String> normalizeKeySet(Set<String> keys) {
    if (keys == null || keys.isEmpty()) return Set.of();
    Set<String> out = new LinkedHashSet<>();
    for (String k : keys) {
      if (k == null) continue;
      String s = k.trim();
      if (!s.isEmpty()) out.add(s);
    }
    return Set.copyOf(out);
  }

  private static Map<String, Set<String>> copySetMap(Map<String, Set<String>> in) {
    if (in == null || in.isEmpty()) return Map.of();
    Map<String, Set<String>> out = new LinkedHashMap<>();
    for (var e : in.entrySet()) {
      String k = e.getKey();
      Set<String> v = e.getValue();
      out.put(k, (v == null || v.isEmpty()) ? Set.of() : Set.copyOf(v));
    }
    return Map.copyOf(out);
  }

  private static List<String> parseGovernanceKeys(Object raw) {
    if (raw == null) return List.of();
    if (raw instanceof String s) {
      String t = s.trim();
      return t.isEmpty() ? List.of() : List.of(t);
    }
    if (raw instanceof Iterable<?> it) {
      List<String> out = new ArrayList<>();
      for (Object o : it) {
        if (o == null) continue;
        String t = String.valueOf(o).trim();
        if (!t.isEmpty()) out.add(t);
      }
      return out.isEmpty() ? List.of() : List.copyOf(out);
    }
    // fallback: single value
    String t = String.valueOf(raw).trim();
    return t.isEmpty() ? List.of() : List.of(t);
  }
}


