package io.intellixity.nativa.persistence.jdbc;

import io.intellixity.nativa.persistence.compile.Bind;
import io.intellixity.nativa.persistence.spi.sql.NativeStatement;

import java.util.List;

public record SqlStatement(String sql, List<Bind> binds, ExecKind execKind) implements NativeStatement {
  public enum ExecKind {
    /** Execute via PreparedStatement.executeQuery() (used for SELECT/COUNT). */
    QUERY,
    /** Execute via PreparedStatement.executeUpdate() (no generated keys). */
    UPDATE,
    /** Execute via PreparedStatement.executeUpdate() + getGeneratedKeys(). */
    UPDATE_GENERATED_KEYS,
    /** Execute via PreparedStatement.executeQuery() and read the first column of the first row (RETURNING/OUTPUT). */
    QUERY_ONE_VALUE
  }

  public SqlStatement {
    binds = binds == null ? List.of() : List.copyOf(binds);
    execKind = (execKind == null) ? ExecKind.QUERY : execKind;
  }

  public SqlStatement(String sql, List<Bind> binds) {
    this(sql, binds, ExecKind.QUERY);
  }
}



