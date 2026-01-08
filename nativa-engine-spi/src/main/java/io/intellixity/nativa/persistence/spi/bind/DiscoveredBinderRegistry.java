package io.intellixity.nativa.persistence.spi.bind;

import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;
import io.intellixity.nativa.persistence.compile.Bind;
import io.intellixity.nativa.persistence.util.NativaFactoriesLoader;

import java.util.*;

/**
 * Binder registry built via discovery (META-INF/nativa.factories).\n
 *
 * Resolution semantics:\n
 * - Dialect-specific providers first, then global providers (dialectId=\"*\").\n
 * - Within a provider, binder order is preserved.\n
 * - First binder that matches targetType/valueType and supports(ctx,bind,value) wins.\n
 */
public final class DiscoveredBinderRegistry {
  public static final String GLOBAL_DIALECT = "*";

  private final String dialectId;
  private final List<Binder<?, ?>> dialectOrdered;
  private final List<Binder<?, ?>> globalOrdered;

  public DiscoveredBinderRegistry(String dialectId) {
    this(dialectId, NativaFactoriesLoader.load(BinderProvider.class));
  }

  DiscoveredBinderRegistry(String dialectId, List<BinderProvider> providers) {
    this.dialectId = (dialectId == null || dialectId.isBlank()) ? "" : dialectId;
    List<Binder<?, ?>> dialect = new ArrayList<>();
    List<Binder<?, ?>> global = new ArrayList<>();

    for (BinderProvider p : providers) {
      if (p == null) continue;
      String did = normalizeDialect(p.dialectId());
      Collection<Binder<?, ?>> bs = p.binders();
      if (bs == null) continue;
      if (GLOBAL_DIALECT.equals(did)) global.addAll(bs);
      else if (Objects.equals(this.dialectId, did)) dialect.addAll(bs);
    }

    this.dialectOrdered = List.copyOf(dialect);
    this.globalOrdered = List.copyOf(global);
  }

  public <TTarget> void bind(TTarget target, BindContext ctx, Bind bind, Object encodedValue, UserTypeRegistry userTypes) {
    if (target == null) throw new IllegalArgumentException("target is required");
    if (ctx == null) throw new IllegalArgumentException("ctx is required");
    if (ctx.opKind() == null) throw new IllegalArgumentException("ctx.opKind is required");
    if (tryBind(dialectOrdered, target, ctx, bind, encodedValue, userTypes)) return;
    if (tryBind(globalOrdered, target, ctx, bind, encodedValue, userTypes)) return;
    throw new IllegalArgumentException("No binder found for dialectId=" + dialectId +
        ", target=" + target.getClass().getName() +
        ", value=" + (encodedValue == null ? "null" : encodedValue.getClass().getName()) +
        ", userTypeId=" + (bind == null ? null : bind.userTypeId()));
  }

  private static <TTarget> boolean tryBind(List<Binder<?, ?>> ordered, TTarget target, BindContext ctx, Bind bind,
                                          Object encodedValue, UserTypeRegistry userTypes) {
    for (Binder<?, ?> b : ordered) {
      if (b == null) continue;
      if (!b.targetType().isInstance(target)) continue;
      if (encodedValue != null && !b.valueType().isInstance(encodedValue)) continue;
      @SuppressWarnings("unchecked")
      Binder<TTarget, Object> bb = (Binder<TTarget, Object>) b;
      if (bb.supports(ctx, bind, encodedValue)) {
        bb.bind(target, ctx, bind, encodedValue, userTypes);
        return true;
      }
    }
    return false;
  }

  private static String normalizeDialect(String did) {
    if (did == null) return GLOBAL_DIALECT;
    String s = did.trim();
    return s.isEmpty() ? GLOBAL_DIALECT : s;
  }
}


