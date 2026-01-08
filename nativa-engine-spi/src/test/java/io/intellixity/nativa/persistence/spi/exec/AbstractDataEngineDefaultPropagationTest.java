package io.intellixity.nativa.persistence.spi.exec;

import io.intellixity.nativa.persistence.authoring.AuthoringRegistry;
import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.ViewDef;
import io.intellixity.nativa.persistence.dmlast.*;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.exec.TxHandle;
import io.intellixity.nativa.persistence.exec.handle.EngineHandle;
import io.intellixity.nativa.persistence.query.QueryElement;
import io.intellixity.nativa.persistence.spi.sql.Dialect;
import io.intellixity.nativa.persistence.spi.sql.NativeStatement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class AbstractDataEngineDefaultPropagationTest {

  private record Stmt() implements NativeStatement {}

  private static final class NoopDialect implements Dialect<Stmt> {
    private final String id;
    NoopDialect(String id) { this.id = id; }
    @Override public String id() { return id; }
    @Override public Stmt mergeSelect(EntityAuthoring ea, ViewDef view, QueryElement filter, List sort,
                                      io.intellixity.nativa.persistence.query.Page page, Map params,
                                      io.intellixity.nativa.persistence.compile.PropertyTypeResolver types) {
      throw new UnsupportedOperationException();
    }
    @Override public Stmt mergeCount(EntityAuthoring ea, ViewDef view, QueryElement filter, Map params,
                                     io.intellixity.nativa.persistence.compile.PropertyTypeResolver types) {
      throw new UnsupportedOperationException();
    }
    @Override public Stmt renderDml(EntityAuthoring ea, ViewDef view, DmlAst dml,
                                    io.intellixity.nativa.persistence.compile.PropertyTypeResolver types) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class NoopPlanner implements DmlPlanner {
    @Override public InsertAst planInsert(EntityAuthoring ea, ViewDef view, Object pojo, boolean returningKey) { throw new UnsupportedOperationException(); }
    @Override public UpdateAst planUpdateById(EntityAuthoring ea, ViewDef view, Object pojo) { throw new UnsupportedOperationException(); }
    @Override public UpdateAst planUpdateByCriteria(EntityAuthoring ea, ViewDef view, Object pojo, QueryElement where) { throw new UnsupportedOperationException(); }
    @Override public DeleteAst planDeleteByCriteria(EntityAuthoring ea, ViewDef view, QueryElement where) { throw new UnsupportedOperationException(); }
    @Override public UpsertAst planUpsert(EntityAuthoring ea, ViewDef view, Object pojo, boolean returningKey) { throw new UnsupportedOperationException(); }
  }

  private static final class NoopAuthoring implements AuthoringRegistry {
    @Override public EntityAuthoring getEntityAuthoring(String authoringId) { throw new UnsupportedOperationException(); }
    @Override public ViewDef getViewDef(String viewDefId) { throw new UnsupportedOperationException(); }
  }

  private record NoopHandle() implements EngineHandle<Object> {
    @Override public String id() { return "noop"; }
    @Override public Object client() { return new Object(); }
    @Override public String namespace() { return "schema"; }
    @Override public boolean multiTenant() { return true; }
  }

  private static final class CountingEngine extends AbstractDataEngine<Stmt, NoopHandle> {
    private final AtomicInteger begins = new AtomicInteger();

    CountingEngine(Propagation defaultPropagation) {
      super(new NoopDialect("test"), new NoopHandle(), new NoopAuthoring(), new NoopPlanner(), defaultPropagation);
    }

    int beginCount() { return begins.get(); }

    @Override protected TxHandle begin() { begins.incrementAndGet(); return new TxHandle() {}; }
    @Override protected void commit(TxHandle tx) {}
    @Override protected void rollback(TxHandle tx) {}

    @Override protected <T> List<T> executeSelect(TxHandle txOrNull, ViewDef view, Stmt stmt, io.intellixity.nativa.persistence.mapping.RowReader<T> reader) { throw new UnsupportedOperationException(); }
    @Override protected long executeCount(TxHandle txOrNull, ViewDef view, Stmt stmt) { throw new UnsupportedOperationException(); }
    @Override protected Object executeInsertForId(TxHandle tx, EntityAuthoring ea, ViewDef view, InsertAst ast, Stmt stmt) { throw new UnsupportedOperationException(); }
    @Override protected Object executeUpsertForId(TxHandle tx, EntityAuthoring ea, ViewDef view, UpsertAst ast, Stmt stmt) { throw new UnsupportedOperationException(); }
    @Override protected long executeUpdate(TxHandle tx, EntityAuthoring ea, ViewDef view, UpdateAst ast, Stmt stmt) { throw new UnsupportedOperationException(); }
    @Override protected long executeDelete(TxHandle tx, EntityAuthoring ea, ViewDef view, DeleteAst ast, Stmt stmt) { throw new UnsupportedOperationException(); }
  }

  @Test
  void inTxSupplier_usesEngineDefaultPropagation_requiredStartsTx() {
    CountingEngine e = new CountingEngine(Propagation.REQUIRED);
    String out = e.inTx(() -> "ok");
    assertEquals("ok", out);
    assertEquals(1, e.beginCount());
  }

  @Test
  void inTxSupplier_usesEngineDefaultPropagation_supportsDoesNotStartTx() {
    CountingEngine e = new CountingEngine(Propagation.SUPPORTS);
    String out = e.inTx(() -> "ok");
    assertEquals("ok", out);
    assertEquals(0, e.beginCount());
  }
}


