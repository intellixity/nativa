package io.intellixity.nativa.persistence.governance;

import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.exec.handle.EngineHandle;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class GovernanceDataEngineResolverTest {

  private record TestHandle(String id, String namespace, boolean multiTenant) implements EngineHandle<Object> {
    @Override public Object client() { return new Object(); }
  }

  private static final class TestEngine implements DataEngine<EngineHandle<?>> {
    private final EngineHandle<?> handle;
    TestEngine(EngineHandle<?> handle) { this.handle = handle; }
    @Override public EngineHandle<?> handle() { return handle; }
    @Override public Propagation defaultPropagation() { return Propagation.SUPPORTS; }
    @Override public <T> T inTx(Propagation propagation, Supplier<T> work) { return work.get(); }
    @Override public <T> java.util.List<T> select(io.intellixity.nativa.persistence.exec.EntityViewRef ref, io.intellixity.nativa.persistence.query.Query query) { throw new UnsupportedOperationException(); }
    @Override public long count(io.intellixity.nativa.persistence.exec.EntityViewRef ref, io.intellixity.nativa.persistence.query.Query query) { throw new UnsupportedOperationException(); }
    @Override public <T> T insert(io.intellixity.nativa.persistence.exec.EntityViewRef ref, T entity) { throw new UnsupportedOperationException(); }
    @Override public <T> void bulkInsert(io.intellixity.nativa.persistence.exec.EntityViewRef ref, java.util.List<T> entities) { throw new UnsupportedOperationException(); }
    @Override public <T> T upsert(io.intellixity.nativa.persistence.exec.EntityViewRef ref, T entity) { throw new UnsupportedOperationException(); }
    @Override public <T> void bulkUpsert(io.intellixity.nativa.persistence.exec.EntityViewRef ref, java.util.List<T> entities) { throw new UnsupportedOperationException(); }
    @Override public <T> long update(io.intellixity.nativa.persistence.exec.EntityViewRef ref, T entity) { throw new UnsupportedOperationException(); }
    @Override public <T> long bulkUpdate(io.intellixity.nativa.persistence.exec.EntityViewRef ref, java.util.List<T> entities) { throw new UnsupportedOperationException(); }
    @Override public <T> long updateByCriteria(io.intellixity.nativa.persistence.exec.EntityViewRef ref, io.intellixity.nativa.persistence.query.Query query, T entity) { throw new UnsupportedOperationException(); }
    @Override public long deleteByCriteria(io.intellixity.nativa.persistence.exec.EntityViewRef ref, io.intellixity.nativa.persistence.query.Query query) { throw new UnsupportedOperationException(); }
  }

  @Test
  void cachesHandleAndEngine_byCtxFamilyReadOnly() {
    AtomicInteger handleCalls = new AtomicInteger();
    AtomicInteger engineCalls = new AtomicInteger();

    EngineHandleResolver hr = new EngineHandleResolver() {
      @Override
      @SuppressWarnings("unchecked")
      public <T extends EngineHandle<?>> T resolve(String family, GovernanceContext ctx, boolean readOnly) {
        handleCalls.incrementAndGet();
        return (T) new TestHandle(family + ":" + ctx.cacheKey() + ":" + readOnly, "ns", true);
      }
    };
    DataEngineFactory ef = (family, handle) -> {
      engineCalls.incrementAndGet();
      return new TestEngine((EngineHandle<?>) handle);
    };

    GovernanceDataEngineResolver r = new GovernanceDataEngineResolver(hr, ef, 10, 10, 60_000);

    GovernanceContext ctx = GovernanceContext.of(Map.of("tenantId", "t1"), "t1");
    DataEngine<?> e1 = r.resolve("jdbc", ctx, false);
    DataEngine<?> e2 = r.resolve("jdbc", ctx, false);

    assertSame(e1, e2);
    assertEquals(1, handleCalls.get());
    assertEquals(1, engineCalls.get());
  }

  @Test
  void differentReadOnly_producesDifferentCaches() {
    AtomicInteger handleCalls = new AtomicInteger();
    AtomicInteger engineCalls = new AtomicInteger();

    EngineHandleResolver hr = new EngineHandleResolver() {
      @Override
      @SuppressWarnings("unchecked")
      public <T extends EngineHandle<?>> T resolve(String family, GovernanceContext ctx, boolean readOnly) {
        handleCalls.incrementAndGet();
        return (T) new TestHandle(family + ":" + ctx.cacheKey() + ":" + readOnly, "ns", true);
      }
    };
    DataEngineFactory ef = (family, handle) -> {
      engineCalls.incrementAndGet();
      return new TestEngine((EngineHandle<?>) handle);
    };

    GovernanceDataEngineResolver r = new GovernanceDataEngineResolver(hr, ef, 10, 10, 60_000);

    GovernanceContext ctx = GovernanceContext.of(Map.of("tenantId", "t1"), "t1");
    DataEngine<?> rw = r.resolve("mongo", ctx, false);
    DataEngine<?> ro = r.resolve("mongo", ctx, true);

    assertNotSame(rw, ro);
    assertEquals(2, handleCalls.get());
    assertEquals(2, engineCalls.get());
  }

  @Test
  void lruEviction_recomputesAfterEvict() {
    AtomicInteger handleCalls = new AtomicInteger();
    AtomicInteger engineCalls = new AtomicInteger();

    EngineHandleResolver hr = new EngineHandleResolver() {
      @Override
      @SuppressWarnings("unchecked")
      public <T extends EngineHandle<?>> T resolve(String family, GovernanceContext ctx, boolean readOnly) {
        handleCalls.incrementAndGet();
        return (T) new TestHandle(family + ":" + ctx.cacheKey() + ":" + readOnly + ":" + handleCalls.get(), "ns", true);
      }
    };
    DataEngineFactory ef = (family, handle) -> {
      engineCalls.incrementAndGet();
      return new TestEngine((EngineHandle<?>) handle);
    };

    // maxHandles=1/maxEngines=1 => second key evicts first
    GovernanceDataEngineResolver r = new GovernanceDataEngineResolver(hr, ef, 1, 1, 60_000);

    GovernanceContext c1 = GovernanceContext.of(Map.of("tenantId", "t1"), "t1");
    GovernanceContext c2 = GovernanceContext.of(Map.of("tenantId", "t2"), "t2");

    DataEngine<?> e1 = r.resolve("jdbc", c1, false);
    DataEngine<?> e2 = r.resolve("jdbc", c2, false);
    assertNotSame(e1, e2);

    // c1 was evicted; resolving again should recompute handle+engine
    DataEngine<?> e1b = r.resolve("jdbc", c1, false);
    assertNotSame(e1, e1b);
    assertTrue(handleCalls.get() >= 3);
    assertTrue(engineCalls.get() >= 3);
  }

  @Test
  void ttlExpiry_recomputesAfterTtl() throws Exception {
    AtomicInteger handleCalls = new AtomicInteger();
    AtomicInteger engineCalls = new AtomicInteger();

    EngineHandleResolver hr = new EngineHandleResolver() {
      @Override
      @SuppressWarnings("unchecked")
      public <T extends EngineHandle<?>> T resolve(String family, GovernanceContext ctx, boolean readOnly) {
        handleCalls.incrementAndGet();
        return (T) new TestHandle(family + ":" + ctx.cacheKey() + ":" + readOnly + ":" + handleCalls.get(), "ns", true);
      }
    };
    DataEngineFactory ef = (family, handle) -> {
      engineCalls.incrementAndGet();
      return new TestEngine((EngineHandle<?>) handle);
    };

    GovernanceDataEngineResolver r = new GovernanceDataEngineResolver(hr, ef, 10, 10, 5);

    GovernanceContext ctx = GovernanceContext.of(Map.of("tenantId", "t1"), "t1");
    DataEngine<?> e1 = r.resolve("mongo", ctx, false);
    Thread.sleep(10);
    DataEngine<?> e2 = r.resolve("mongo", ctx, false);

    assertNotSame(e1, e2);
    assertTrue(handleCalls.get() >= 2);
    assertTrue(engineCalls.get() >= 2);
  }
}


