package io.intellixity.nativa.persistence.governance;

import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.exec.handle.EngineHandle;
import io.intellixity.nativa.persistence.governance.internal.LruTtlCache;

import java.util.Objects;

/**
 * Cache-backed resolver that turns (engineFamily, governance context, readOnly) into a cached {@link DataEngine}.\n
 *
 * Caches:\n
 * - EngineHandle by (engineFamily, readOnly, ctx.cacheKey)\n
 * - DataEngine by (engineFamily, readOnly, handle.id)\n
 */
public final class GovernanceDataEngineResolver {
  private final EngineHandleResolver handleResolver;
  private final DataEngineFactory engineFactory;

  private final LruTtlCache<HandleKey, EngineHandle<?>> handles;
  private final LruTtlCache<EngineKey, DataEngine<? extends EngineHandle<?>>> engines;

  public GovernanceDataEngineResolver(EngineHandleResolver handleResolver,
                                      DataEngineFactory engineFactory,
                                      int maxHandles,
                                      int maxEngines,
                                      long ttlMillis) {
    this(handleResolver, engineFactory, maxHandles, maxEngines, ttlMillis, 0);
  }

  public GovernanceDataEngineResolver(EngineHandleResolver handleResolver,
                                      DataEngineFactory engineFactory,
                                      int maxHandles,
                                      int maxEngines,
                                      long ttlMillis,
                                      long idleMillis) {
    this.handleResolver = Objects.requireNonNull(handleResolver, "handleResolver");
    this.engineFactory = Objects.requireNonNull(engineFactory, "engineFactory");
    this.handles = new LruTtlCache<>(maxHandles, ttlMillis, idleMillis);
    this.engines = new LruTtlCache<>(maxEngines, ttlMillis, idleMillis);
  }

  public DataEngine<?> resolve(String engineFamily, GovernanceContext ctx, boolean readOnly) {
    Objects.requireNonNull(engineFamily, "engineFamily");
    Objects.requireNonNull(ctx, "ctx");
    String family = engineFamily.trim();
    if (family.isEmpty()) throw new IllegalArgumentException("engineFamily is blank");

    HandleKey hk = new HandleKey(family, readOnly, ctx.cacheKey());
    EngineHandle<?> handle = handles.getOrCompute(hk, () -> handleResolver.resolve(family, ctx, readOnly));
    if (handle == null) throw new IllegalStateException("EngineHandleResolver returned null for " + hk);

    EngineKey ek = new EngineKey(family, readOnly, handle.id());
    DataEngine<? extends EngineHandle<?>> engine = engines.getOrCompute(ek, () -> engineFactory.create(family, handle));
    if (engine == null) throw new IllegalStateException("DataEngineFactory returned null for " + ek);

    return engine;
  }

  private record HandleKey(String engineFamily, boolean readOnly, String governanceCacheKey) {}
  private record EngineKey(String engineFamily, boolean readOnly, String handleId) {}
}




