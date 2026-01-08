package io.intellixity.nativa.persistence.jdbc;

import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.spi.bind.BindOpKind;
import io.intellixity.nativa.persistence.dmlast.InsertAst;
import io.intellixity.nativa.persistence.dmlast.UpdateAst;
import io.intellixity.nativa.persistence.dmlast.DeleteAst;
import io.intellixity.nativa.persistence.dmlast.UpsertAst;
import io.intellixity.nativa.persistence.dmlast.DmlPlanner;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.exec.TxHandle;
import io.intellixity.nativa.persistence.spi.exec.AbstractDataEngine;
import io.intellixity.nativa.persistence.jdbc.bind.DefaultJdbcBindContext;
import io.intellixity.nativa.persistence.jdbc.dialect.JdbcDialect;
import io.intellixity.nativa.persistence.mapping.RowReader;
import io.intellixity.nativa.persistence.mapping.ViewMappedRowAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public final class JdbcDataEngine extends AbstractDataEngine<SqlStatement, JdbcHandle> {
  private static final Logger log = LoggerFactory.getLogger(JdbcDataEngine.class);
  private final javax.sql.DataSource ds;

  public JdbcDataEngine(JdbcHandle handle,
                        AuthoringRegistry authoring,
                        JdbcDialect dialect,
                        DmlPlanner dmlPlanner,
                        Propagation defaultPropagation) {
    super(dialect,
        Objects.requireNonNull(handle, "handle"),
        Objects.requireNonNull(authoring, "authoring"),
        Objects.requireNonNull(dmlPlanner, "dmlPlanner"),
        defaultPropagation);

    this.ds = handle.client();
  }

  /** Backward-compatible constructor: wraps raw client+schema into a handle. */
  public JdbcDataEngine(javax.sql.DataSource ds,
                        AuthoringRegistry authoring,
                        JdbcDialect dialect,
                        DmlPlanner dmlPlanner,
                        String schemaName,
                        Propagation defaultPropagation) {
    this(new JdbcHandle("jdbc", ds, schemaName, true), authoring, dialect, dmlPlanner, defaultPropagation);
  }

  /** Backward-compatible constructor: wraps raw client+schema into a handle. */
  public JdbcDataEngine(javax.sql.DataSource ds,
                        AuthoringRegistry authoring,
                        JdbcDialect dialect,
                        DmlPlanner dmlPlanner,
                        String schemaName) {
    this(ds, authoring, dialect, dmlPlanner, schemaName, Propagation.REQUIRED);
  }

  @Override
  protected TxHandle begin() {
    try {
      Connection c = ds.getConnection();
      c.setAutoCommit(false);
      return new JdbcTxHandle(c);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void commit(TxHandle tx) {
    JdbcTxHandle j = (JdbcTxHandle) tx;
    try { j.conn.commit(); j.conn.close(); } catch (SQLException e) { throw new RuntimeException(e); }
  }

  @Override
  protected void rollback(TxHandle tx) {
    JdbcTxHandle j = (JdbcTxHandle) tx;
    try { j.conn.rollback(); j.conn.close(); } catch (SQLException e) { throw new RuntimeException(e); }
  }

  @Override
  protected <T> List<T> executeSelect(TxHandle txOrNull, ViewDef view, SqlStatement ss, RowReader<T> reader) {
    try {
      Connection c = (txOrNull == null) ? ds.getConnection() : ((JdbcTxHandle) txOrNull).conn;
      try {
        String jdbcSql = ViewSqlParamCompiler.toJdbcSql(ss.sql());
        long start = System.nanoTime();
        debugSql("SELECT", ss, jdbcSql, BindOpKind.FILTER);
        try (PreparedStatement ps = c.prepareStatement(jdbcSql)) {
          bindAll(ps, ss, BindOpKind.FILTER);
          try (ResultSet rs = ps.executeQuery()) {
            List<T> out = new ArrayList<>();
            JdbcRowAdapter baseRow = new JdbcRowAdapter(rs, userTypes());
            var row = new ViewMappedRowAdapter(baseRow, view);
            while (rs.next()) out.add(reader.read(row));
            debugDone("SELECT", ss, jdbcSql, out.size(), System.nanoTime() - start);
            return out;
          }
        }
      } finally {
        if (txOrNull == null) c.close();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected long executeCount(TxHandle txOrNull, ViewDef view, SqlStatement ss) {
    try {
      Connection c = (txOrNull == null) ? ds.getConnection() : ((JdbcTxHandle) txOrNull).conn;
      try {
        String jdbcSql = ViewSqlParamCompiler.toJdbcSql(ss.sql());
        long start = System.nanoTime();
        debugSql("COUNT", ss, jdbcSql, BindOpKind.FILTER);
        try (PreparedStatement ps = c.prepareStatement(jdbcSql)) {
          bindAll(ps, ss, BindOpKind.FILTER);
          try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return 0;
            long v = rs.getLong(1);
            debugDone("COUNT", ss, jdbcSql, v, System.nanoTime() - start);
            return v;
          }
        }
      } finally {
        if (txOrNull == null) c.close();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected Object executeInsertForId(TxHandle tx, EntityAuthoring ea, ViewDef view,
                                      InsertAst ast, SqlStatement ss) {
    try {
      Connection c = (tx == null) ? ds.getConnection() : ((JdbcTxHandle) tx).conn;
      try {
        String jdbcSql = ViewSqlParamCompiler.toJdbcSql(ss.sql());
        long start = System.nanoTime();
        debugSql("INSERT", ss, jdbcSql, BindOpKind.INSERT);
        return switch (ss.execKind()) {
          case QUERY_ONE_VALUE -> {
            try (PreparedStatement ps = c.prepareStatement(jdbcSql)) {
              bindAll(ps, ss, BindOpKind.INSERT);
              try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) yield null;
                Object v = rs.getObject(1);
                debugDone("INSERT", ss, jdbcSql, "returning", System.nanoTime() - start);
                yield v;
              }
            }
          }
          case UPDATE_GENERATED_KEYS -> {
            try (PreparedStatement ps = c.prepareStatement(jdbcSql, Statement.RETURN_GENERATED_KEYS)) {
              bindAll(ps, ss, BindOpKind.INSERT);
              int n = ps.executeUpdate();
              try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs == null || !rs.next()) yield null;
                Object v = rs.getObject(1);
                debugDone("INSERT", ss, jdbcSql, n, System.nanoTime() - start);
                yield v;
              }
            }
          }
          case UPDATE -> {
            try (PreparedStatement ps = c.prepareStatement(jdbcSql)) {
              bindAll(ps, ss, BindOpKind.INSERT);
              int n = ps.executeUpdate();
              debugDone("INSERT", ss, jdbcSql, n, System.nanoTime() - start);
              yield null;
            }
          }
          case QUERY -> throw new IllegalArgumentException("Invalid execKind=QUERY for insert; use QUERY_ONE_VALUE/UPDATE/UPDATE_GENERATED_KEYS");
        };
      } finally {
        if (tx == null) c.close();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected Object executeUpsertForId(TxHandle tx, EntityAuthoring ea, ViewDef view,
                                      UpsertAst ast, SqlStatement ss) {
    try {
      Connection c = (tx == null) ? ds.getConnection() : ((JdbcTxHandle) tx).conn;
      try {
        String jdbcSql = ViewSqlParamCompiler.toJdbcSql(ss.sql());
        long start = System.nanoTime();
        debugSql("UPSERT", ss, jdbcSql, BindOpKind.UPSERT_SET);
        return switch (ss.execKind()) {
          case QUERY_ONE_VALUE -> {
            try (PreparedStatement ps = c.prepareStatement(jdbcSql)) {
              bindAll(ps, ss, BindOpKind.UPSERT_SET);
              try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) yield null;
                Object v = rs.getObject(1);
                debugDone("UPSERT", ss, jdbcSql, "returning", System.nanoTime() - start);
                yield v;
              }
            }
          }
          case UPDATE_GENERATED_KEYS -> {
            try (PreparedStatement ps = c.prepareStatement(jdbcSql, Statement.RETURN_GENERATED_KEYS)) {
              bindAll(ps, ss, BindOpKind.UPSERT_SET);
              int n = ps.executeUpdate();
              try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs == null || !rs.next()) yield null;
                Object v = rs.getObject(1);
                debugDone("UPSERT", ss, jdbcSql, n, System.nanoTime() - start);
                yield v;
              }
            }
          }
          case UPDATE -> {
            try (PreparedStatement ps = c.prepareStatement(jdbcSql)) {
              bindAll(ps, ss, BindOpKind.UPSERT_SET);
              int n = ps.executeUpdate();
              debugDone("UPSERT", ss, jdbcSql, n, System.nanoTime() - start);
              yield null;
            }
          }
          case QUERY -> throw new IllegalArgumentException("Invalid execKind=QUERY for upsert; use QUERY_ONE_VALUE/UPDATE/UPDATE_GENERATED_KEYS");
        };
      } finally {
        if (tx == null) c.close();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  // insertByCriteria removed for now

  @Override
  protected long executeUpdate(TxHandle tx, EntityAuthoring ea, ViewDef view, UpdateAst ast, SqlStatement ss) {
    try {
      Connection c = (tx == null) ? ds.getConnection() : ((JdbcTxHandle) tx).conn;
      try {
        String jdbcSql = ViewSqlParamCompiler.toJdbcSql(ss.sql());
        long start = System.nanoTime();
        debugSql("UPDATE", ss, jdbcSql, BindOpKind.UPDATE_SET);
        try (PreparedStatement ps = c.prepareStatement(jdbcSql)) {
          bindAll(ps, ss, BindOpKind.UPDATE_SET);
          long n = ps.executeUpdate();
          debugDone("UPDATE", ss, jdbcSql, n, System.nanoTime() - start);
          return n;
        }
      } finally {
        if (tx == null) c.close();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected long executeDelete(TxHandle tx, EntityAuthoring ea, ViewDef view, DeleteAst ast, SqlStatement ss) {
    try {
      Connection c = (tx == null) ? ds.getConnection() : ((JdbcTxHandle) tx).conn;
      try {
        String jdbcSql = ViewSqlParamCompiler.toJdbcSql(ss.sql());
        long start = System.nanoTime();
        debugSql("DELETE", ss, jdbcSql, BindOpKind.FILTER);
        try (PreparedStatement ps = c.prepareStatement(jdbcSql)) {
          bindAll(ps, ss, BindOpKind.FILTER);
          long n = ps.executeUpdate();
          debugDone("DELETE", ss, jdbcSql, n, System.nanoTime() - start);
          return n;
        }
      } finally {
        if (tx == null) c.close();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public record JdbcTxHandle(Connection conn) implements TxHandle {}

  private void bindAll(PreparedStatement ps, SqlStatement stmt, BindOpKind opKind) {
    for (int i = 0; i < stmt.binds().size(); i++) {
      var b = stmt.binds().get(i);
      Object encoded = encode(b);
      var ctx = new DefaultJdbcBindContext(opKind, i + 1);
      bindInto(ps, ctx, b, encoded);
    }
  }

  private void debugSql(String op, SqlStatement ss, String jdbcSql, BindOpKind bindKind) {
    if (!log.isDebugEnabled()) return;
    JdbcHandle h = handle();
    log.debug("nativa.jdbc op={} execKind={} bindKind={} bindCount={} handleId={} schema={} sql={}",
        op, ss.execKind(), bindKind, ss.binds() == null ? 0 : ss.binds().size(),
        h == null ? "null" : h.id(),
        h == null ? "null" : h.schema(),
        jdbcSql);

    // TRACE: bind summary only (no raw values; avoids PII leaks)
    if (log.isTraceEnabled() && ss.binds() != null && !ss.binds().isEmpty()) {
      int idx = 1;
      for (var b : ss.binds()) {
        Object v = (b == null) ? null : b.value();
        String vType = (v == null) ? "null" : v.getClass().getName();
        int vLen = (v instanceof CharSequence cs) ? cs.length() : -1;
        log.trace("nativa.jdbc bind index={} userTypeId={} valueType={} valueLen={}",
            idx++, b == null ? "null" : b.userTypeId(), vType, vLen);
      }
    }
  }

  private void debugDone(String op, SqlStatement ss, String jdbcSql, Object result, long durationNanos) {
    if (!log.isDebugEnabled()) return;
    log.debug("nativa.jdbc_done op={} execKind={} durationMs={} result={}",
        op, ss.execKind(), durationNanos / 1_000_000.0, safeResult(result));
  }

  private static String safeResult(Object r) {
    if (r == null) return "null";
    if (r instanceof Number n) return String.valueOf(n);
    if (r instanceof CharSequence cs) return "len=" + cs.length();
    return r.getClass().getSimpleName();
  }
}

