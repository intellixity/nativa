package io.intellixity.nativa.persistence.spi.exec;

import io.intellixity.nativa.persistence.authoring.AuthoringRegistry;
import io.intellixity.nativa.persistence.authoring.DiscoveredUserTypeRegistry;
import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.FieldDef;
import io.intellixity.nativa.persistence.authoring.SqlViewDef;
import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;
import io.intellixity.nativa.persistence.authoring.ViewDef;
import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.exec.EntityViewRef;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.exec.TxHandle;
import io.intellixity.nativa.persistence.exec.handle.EngineHandle;
import io.intellixity.nativa.persistence.spi.bind.BindContext;
import io.intellixity.nativa.persistence.spi.bind.DiscoveredBinderRegistry;
import io.intellixity.nativa.persistence.compile.Bind;
import io.intellixity.nativa.persistence.compile.QueryNormalizer;
import io.intellixity.nativa.persistence.compile.PropertyTypeResolver;
import io.intellixity.nativa.persistence.dmlast.*;
import io.intellixity.nativa.persistence.mapping.RowReader;
import io.intellixity.nativa.persistence.mapping.DiscoveredRowReaderRegistry;
import io.intellixity.nativa.persistence.query.Query;
import io.intellixity.nativa.persistence.query.QueryElement;
import io.intellixity.nativa.persistence.spi.sql.Dialect;
import io.intellixity.nativa.persistence.spi.sql.NativeStatement;

import java.lang.ScopedValue;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Template-method orchestrator for persistence operations.\n
 *
 * Responsibilities:\n
 * - Transaction scoping via {@link #inTx(Propagation, java.util.function.Supplier)}\n
 * - Normalize Query DSL -> QueryElement via {@link QueryNormalizer}\n
 * - Build native statements using {@link Dialect} and {@link DmlPlanner}\n
 * - Delegate execution to backend-specific hooks\n
 */
public abstract class AbstractDataEngine<S extends NativeStatement, H extends EngineHandle<?>> implements DataEngine<H> {
  private final H handle;
  private final Dialect<S> dialect;
  private final AuthoringRegistry authoring;
  private final QueryNormalizer queryNormalizer;
  private final PropertyTypeResolver propertyTypes;
  private final QueryValidationStrategy queryValidation;
  private final DmlPlanner dmlPlanner;
  private final UserTypeRegistry userTypes;
  private final DiscoveredBinderRegistry binders;
  private final DiscoveredRowReaderRegistry rowReaders;
  private final Propagation defaultPropagation;
  /**
   * Engine-scoped transaction slot.\n
   *
   * We must not accidentally reuse a transaction created by a different DataEngine instance running
   * in the same thread/scope. So we bind (engineMarker, tx) together and only reuse if marker matches.\n
   */
  private static final ScopedValue<TxSlot> TX = ScopedValue.newInstance();
  private final Object txMarker = new Object();

  private record TxSlot(Object marker, TxHandle tx) {}

  /**
   * DI-friendly constructor: callers provide the supporting registries/resolvers.\n
   *
   * This makes it easy to override behavior in tests or in applications without modifying engines.\n
   */
  protected AbstractDataEngine(Dialect<S> dialect,
                               H handle,
                               AuthoringRegistry authoring,
                               DmlPlanner dmlPlanner,
                               Propagation defaultPropagation,
                               QueryNormalizer queryNormalizer,
                               PropertyTypeResolver propertyTypes,
                               QueryValidationStrategy queryValidation,
                               UserTypeRegistry userTypes,
                               DiscoveredBinderRegistry binders,
                               DiscoveredRowReaderRegistry rowReaders) {
    this.handle = Objects.requireNonNull(handle, "handle");
    this.dialect = Objects.requireNonNull(dialect, "dialect");
    this.authoring = Objects.requireNonNull(authoring, "authoring");
    this.defaultPropagation = (defaultPropagation == null) ? Propagation.REQUIRED : defaultPropagation;
    this.queryNormalizer = Objects.requireNonNull(queryNormalizer, "queryNormalizer");
    this.propertyTypes = Objects.requireNonNull(propertyTypes, "propertyTypes");
    this.queryValidation = (queryValidation == null) ? new DefaultQueryValidationStrategy() : queryValidation;
    this.dmlPlanner = Objects.requireNonNull(dmlPlanner, "dmlPlanner");
    this.userTypes = Objects.requireNonNull(userTypes, "userTypes");
    this.binders = Objects.requireNonNull(binders, "binders");
    this.rowReaders = Objects.requireNonNull(rowReaders, "rowReaders");
  }

  protected AbstractDataEngine(Dialect<S> dialect,
                               H handle,
                               AuthoringRegistry authoring,
                               DmlPlanner dmlPlanner,
                               Propagation defaultPropagation) {
    this(
        dialect,
        handle,
        authoring,
        dmlPlanner,
        defaultPropagation,
        new QueryNormalizer(),
        new PropertyTypeResolver(Objects.requireNonNull(authoring, "authoring")),
        new DefaultQueryValidationStrategy(),
        new DiscoveredUserTypeRegistry(Objects.requireNonNull(dialect, "dialect").id()),
        new DiscoveredBinderRegistry(Objects.requireNonNull(dialect, "dialect").id()),
        new DiscoveredRowReaderRegistry()
    );
  }

  protected AbstractDataEngine(Dialect<S> dialect,
                               H handle,
                               AuthoringRegistry authoring,
                               DmlPlanner dmlPlanner) {
    this(
        dialect,
        handle,
        authoring,
        dmlPlanner,
        Propagation.REQUIRED,
        new QueryNormalizer(),
        new PropertyTypeResolver(Objects.requireNonNull(authoring, "authoring")),
        new DefaultQueryValidationStrategy(),
        new DiscoveredUserTypeRegistry(Objects.requireNonNull(dialect, "dialect").id()),
        new DiscoveredBinderRegistry(Objects.requireNonNull(dialect, "dialect").id()),
        new DiscoveredRowReaderRegistry()
    );
  }

  /** Backend-specific transaction begin (write operations may auto-create tx via {@link #inTx(Supplier)}). */
  protected abstract TxHandle begin();

  /** Backend-specific transaction commit (paired with {@link #begin()}). */
  protected abstract void commit(TxHandle tx);

  /** Backend-specific transaction rollback (paired with {@link #begin()}). */
  protected abstract void rollback(TxHandle tx);

  @Override
  public final Propagation defaultPropagation() {
    return defaultPropagation;
  }

  /**
   * Default propagation used by write operations (insert/update/upsert/delete) when the caller
   * did not explicitly wrap the work in {@link DataEngine#inTx(Propagation, Supplier)}.
   */
  protected Propagation defaultWritePropagation() { return defaultPropagation; }

  protected final TxHandle currentTxOrNull() {
    if (!TX.isBound()) return null;
    TxSlot slot = TX.get();
    return (slot != null && slot.marker == this.txMarker) ? slot.tx : null;
  }

  @Override
  public <T> T inTx(Supplier<T> work) {
    return inTx(defaultPropagation, work);
  }

  @Override
  public final <T> T inTx(Propagation propagation, Supplier<T> work) {
    Objects.requireNonNull(propagation, "propagation");
    Objects.requireNonNull(work, "work");
    TxHandle existing = currentTxOrNull();
    return switch (propagation) {
      case REQUIRED -> (existing != null) ? work.get() : runInNewTx(work);
      case SUPPORTS -> work.get();
      case MANDATORY -> {
        if (existing == null) throw new IllegalStateException("No existing transaction for propagation=MANDATORY");
        yield work.get();
      }
      case REQUIRES_NEW -> runInNewTx(work);
      case NEVER -> {
        if (existing != null) throw new IllegalStateException("Existing transaction found for propagation=NEVER");
        yield work.get();
      }
      case NESTED -> (existing != null) ? work.get() : runInNewTx(work);
    };
  }

  private <T> T runInNewTx(Supplier<T> work) {
    TxHandle tx = begin();
    try {
      T result = ScopedValue.where(TX, new TxSlot(this.txMarker, tx)).call(work::get);
      commit(tx);
      return result;
    } catch (Throwable t) {
      try { rollback(tx); } catch (Throwable ignored) {}
      if (t instanceof RuntimeException re) throw re;
      if (t instanceof Error e) throw e;
      throw new RuntimeException(t);
    }
  }

  protected final Dialect<S> dialect() { return dialect; }
  @Override
  public final H handle() { return handle; }
  protected final H engineHandle() { return handle; }
  protected final QueryNormalizer queryNormalizer() { return queryNormalizer; }
  protected final PropertyTypeResolver propertyTypes() { return propertyTypes; }
  protected QueryValidationStrategy queryValidation() { return queryValidation; }
  protected final DmlPlanner dmlPlanner() { return dmlPlanner; }
  protected final UserTypeRegistry userTypes() { return userTypes; }
  protected final DiscoveredBinderRegistry binders() { return binders; }
  protected final String schemaNameOrNull() { return handle.namespace(); }

  /** Apply schema placeholder substitution to view.sqlView.sql/projection if schema=true and value is String. */
  protected final ViewDef resolveSchema(ViewDef view) {
    if (view == null || view.sqlView() == null) return view;
    SqlViewDef sv = view.sqlView();
    if (!sv.schema()) return view;
    String schemaName = handle.namespace();
    if (schemaName == null || schemaName.isBlank()) {
      throw new IllegalArgumentException("SqlViewDef.schema=true but engine has no schemaName");
    }

    Object sql = substituteIfString(sv.sql());
    Object proj = substituteIfString(sv.projection());
    SqlViewDef out = new SqlViewDef(sql, proj, sv.schema(), sv.aliases());
    return new ViewDef(view.id(), view.mapping(), out);
  }

  private Object substituteIfString(Object o) {
    if (!(o instanceof String s)) return o;
    String schemaName = handle.namespace();
    return s.replace("{schema}", schemaName == null ? "" : schemaName);
  }

  /** Encode a value using the resolved UserType for the current dialect. */
  protected final Object encode(Bind bind) {
    if (bind == null) return null;
    @SuppressWarnings("unchecked")
    var ut = (io.intellixity.nativa.persistence.authoring.UserType<Object>) userTypes.get(bind.userTypeId());
    return ut.encode(bind.value());
  }

  /** Bind an already encoded value into the given native target using discovered binders. */
  protected final <TTarget> void bindInto(TTarget target, BindContext ctx, Bind bind, Object encodedValue) {
    binders.bind(target, ctx, bind, encodedValue, userTypes);
  }

  // --- Reads (no auto-tx creation) ---

  @Override
  public final <T> List<T> select(EntityViewRef ref, Query query) {
    ResolvedEntityView rev = resolve(ref);
    EntityAuthoring ea = rev.entityAuthoring();
    ViewDef view = rev.viewDef();
    @SuppressWarnings("unchecked")
    RowReader<T> reader = (RowReader<T>) rowReaders.get(ea.type());
    Query effective = (query == null) ? new Query() : query;
    QueryElement filter = queryNormalizer.normalize(ea, effective);
    queryValidation().validate(ea, view, effective, filter, propertyTypes);
    S stmt = buildSelectStatement(ea, view, effective, filter);
    return executeSelect(currentTxOrNull(), view, stmt, reader);
  }

  @Override
  public final long count(EntityViewRef ref, Query query) {
    ResolvedEntityView rev = resolve(ref);
    EntityAuthoring ea = rev.entityAuthoring();
    ViewDef view = rev.viewDef();
    Query effective = (query == null) ? new Query() : query;
    QueryElement filter = queryNormalizer.normalize(ea, effective);
    queryValidation().validate(ea, view, effective, filter, propertyTypes);
    S stmt = buildCountStatement(ea, view, effective, filter);
    return executeCount(currentTxOrNull(), view, stmt);
  }

  // --- Writes (auto-tx creation) ---

  @Override
  public final <T> T insert(EntityViewRef ref, T entity) {
    ResolvedEntityView rev = resolve(ref);
    EntityAuthoring ea = rev.entityAuthoring();
    ViewDef view = rev.viewDef();
    return inTx(defaultWritePropagation(), () -> {
      InsertAst ast = dmlPlanner.planInsert(ea, view, entity, true);
      S stmt = dialect.renderDml(ea, view, ast, propertyTypes);
      Object rawId = executeInsertForId(currentTxOrNull(), ea, view, ast, stmt);
      applyGeneratedIdIfNeeded(ea, view, ast, entity, rawId);
      return entity;
    });
  }

  @Override
  public final <T> void bulkInsert(EntityViewRef ref, List<T> entities) {
    inTx(defaultWritePropagation(), () -> {
      for (T e : entities) insert(ref, e);
      return null;
    });
  }

  @Override
  public final <T> T upsert(EntityViewRef ref, T entity) {
    ResolvedEntityView rev = resolve(ref);
    EntityAuthoring ea = rev.entityAuthoring();
    ViewDef view = rev.viewDef();
    return inTx(defaultWritePropagation(), () -> {
      UpsertAst ast = dmlPlanner.planUpsert(ea, view, entity, true);
      S stmt = dialect.renderDml(ea, view, ast, propertyTypes);
      Object rawId = executeUpsertForId(currentTxOrNull(), ea, view, ast, stmt);
      applyGeneratedIdIfNeeded(ea, view, ast.insert(), entity, rawId);
      return entity;
    });
  }

  @Override
  public final <T> void bulkUpsert(EntityViewRef ref, List<T> entities) {
    inTx(defaultWritePropagation(), () -> {
      for (T e : entities) upsert(ref, e);
      return null;
    });
  }

  /**
   * If the entity has an auto-generated key field and the insert didn't include an explicit value for that field,
   * populate it on the (mutable) POJO using its generated fluent setter.
   */
  protected final void applyGeneratedIdIfNeeded(EntityAuthoring ea, ViewDef view, InsertAst ast, Object entity, Object rawId) {
    if (entity == null) return;
    if (rawId == null) return;

    // Find auto-generated field: prefer (key && autoGenerated), else (autoGenerated), else none.
    String field = null;
    FieldDef fd = null;
    for (var e : ea.fields().entrySet()) {
      if (e.getValue() != null && e.getValue().autoGenerated() && e.getValue().key()) {
        field = e.getKey();
        fd = e.getValue();
        break;
      }
    }
    if (field == null) {
      for (var e : ea.fields().entrySet()) {
        if (e.getValue() != null && e.getValue().autoGenerated()) {
          field = e.getKey();
          fd = e.getValue();
          break;
        }
      }
    }
    if (field == null || fd == null) return;

    String col = io.intellixity.nativa.persistence.authoring.ViewMappings.ref(view, field);
    if (col == null) return;

    // If the caller explicitly provided an ID, the insert AST will include that column.
    boolean explicit = false;
    for (ColumnBind cb : ast.columns()) {
      if (col.equals(cb.column()) && cb.bind() != null && cb.bind().value() != null) {
        explicit = true;
        break;
      }
    }
    if (explicit) return;

    String userTypeId = null;
    if (fd.type() instanceof io.intellixity.nativa.persistence.authoring.ScalarTypeRef s) {
      userTypeId = s.userTypeId();
    }
    if (userTypeId == null) return;

    Object decoded = userTypes.get(userTypeId).decode(rawId);
    invokeFluentSetter(entity, field, decoded);
  }

  private static void invokeFluentSetter(Object target, String field, Object value) {
    if (target == null) return;
    if (field == null || field.isBlank()) return;
    if (value == null) return;

    // Generated POJOs expose fluent setters named exactly as the field: Order id(UUID id) { ...; return this; }
    try {
      // Prefer exact param match
      var m = target.getClass().getMethod(field, value.getClass());
      m.invoke(target, value);
      return;
    } catch (NoSuchMethodException ignored) {
      // Try any single-arg method with same name and assignable parameter
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }

    for (var m : target.getClass().getMethods()) {
      if (!m.getName().equals(field)) continue;
      if (m.getParameterCount() != 1) continue;
      Class<?> pt = m.getParameterTypes()[0];
      if (!pt.isAssignableFrom(value.getClass())) continue;
      try {
        m.invoke(target, value);
        return;
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /** Template hook: build select statement (default delegates to dialect.mergeSelect). */
  protected S buildSelectStatement(EntityAuthoring ea, ViewDef view, Query effectiveQuery, QueryElement filter) {
    ViewDef v = resolveSchema(view);
    return dialect.mergeSelect(ea, v, filter, effectiveQuery.sort(), effectiveQuery.page(), effectiveQuery.params(), propertyTypes);
  }

  /** Template hook: build count statement (default delegates to dialect.mergeCount). */
  protected S buildCountStatement(EntityAuthoring ea, ViewDef view, Query effectiveQuery, QueryElement filter) {
    ViewDef v = resolveSchema(view);
    return dialect.mergeCount(ea, v, filter, effectiveQuery.params(), propertyTypes);
  }


  @Override
  public final <T> long update(EntityViewRef ref, T entity) {
    ResolvedEntityView rev = resolve(ref);
    EntityAuthoring ea = rev.entityAuthoring();
    ViewDef view = rev.viewDef();
    return inTx(defaultWritePropagation(), () -> {
      UpdateAst ast = dmlPlanner.planUpdateById(ea, view, entity);
      S stmt = dialect.renderDml(ea, view, ast, propertyTypes);
      return executeUpdate(currentTxOrNull(), ea, view, ast, stmt);
    });
  }

  @Override
  public final <T> long bulkUpdate(EntityViewRef ref, List<T> entities) {
    return inTx(defaultWritePropagation(), () -> {
      long total = 0;
      for (T e : entities) total += update(ref, e);
      return total;
    });
  }

  @Override
  public final <T> long updateByCriteria(EntityViewRef ref, Query query, T entity) {
    ResolvedEntityView rev = resolve(ref);
    EntityAuthoring ea = rev.entityAuthoring();
    ViewDef view = rev.viewDef();
    return inTx(defaultWritePropagation(), () -> {
      Query effective = (query == null) ? new Query() : query;
      QueryElement where = queryNormalizer.normalize(ea, effective);
      queryValidation().validate(ea, view, effective, where, propertyTypes);
      UpdateAst ast = dmlPlanner.planUpdateByCriteria(ea, view, entity, where);
      S stmt = dialect.renderDml(ea, view, ast, propertyTypes);
      return executeUpdate(currentTxOrNull(), ea, view, ast, stmt);
    });
  }

  @Override
  public final long deleteByCriteria(EntityViewRef ref, Query query) {
    ResolvedEntityView rev = resolve(ref);
    EntityAuthoring ea = rev.entityAuthoring();
    ViewDef view = rev.viewDef();
    return inTx(defaultWritePropagation(), () -> {
      Query effective = (query == null) ? new Query() : query;
      QueryElement where = queryNormalizer.normalize(ea, effective);
      queryValidation().validate(ea, view, effective, where, propertyTypes);
      DeleteAst ast = dmlPlanner.planDeleteByCriteria(ea, view, where);
      S stmt = dialect.renderDml(ea, view, ast, propertyTypes);
      return executeDelete(currentTxOrNull(), ea, view, ast, stmt);
    });
  }

  protected final ResolvedEntityView resolve(EntityViewRef ref) {
    if (ref == null) throw new IllegalArgumentException("ref is required");
    EntityAuthoring ea = authoring.getEntityAuthoring(ref.type());
    ViewDef view = authoring.getViewDef(ref.viewDefId());
    return new ResolvedEntityView(ea, view);
  }

  // --- Backend-specific hooks ---

  protected abstract <T> List<T> executeSelect(TxHandle txOrNull, ViewDef view, S stmt, RowReader<T> reader);

  protected abstract long executeCount(TxHandle txOrNull, ViewDef view, S stmt);

  protected abstract Object executeInsertForId(TxHandle tx, EntityAuthoring ea, ViewDef view,
                                               InsertAst ast, S stmt);

  protected abstract Object executeUpsertForId(TxHandle tx, EntityAuthoring ea, ViewDef view,
                                               UpsertAst ast, S stmt);

  protected abstract long executeUpdate(TxHandle tx, EntityAuthoring ea, ViewDef view, UpdateAst ast, S stmt);

  protected abstract long executeDelete(TxHandle tx, EntityAuthoring ea, ViewDef view, DeleteAst ast, S stmt);
}


