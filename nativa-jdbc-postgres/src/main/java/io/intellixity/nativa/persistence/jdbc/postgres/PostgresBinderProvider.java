package io.intellixity.nativa.persistence.jdbc.postgres;

import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;
import io.intellixity.nativa.persistence.spi.bind.*;
import io.intellixity.nativa.persistence.jdbc.bind.JdbcBinderProvider;
import io.intellixity.nativa.persistence.jdbc.bind.JdbcBindContext;
import io.intellixity.nativa.persistence.compile.Bind;
import org.postgresql.util.PGobject;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Postgres-specific JDBC binders (dialectId="postgres"). */
public final class PostgresBinderProvider extends JdbcBinderProvider {
  @Override
  public String dialectId() {
    return "postgres";
  }

  @Override
  protected Collection<Binder<?, ?>> dialectBinders() {
    return List.of(new PostgresJsonbBinder(), new PostgresArrayBinder());
  }

  static final class PostgresJsonbBinder implements Binder<PreparedStatement, Object> {
    @Override public Class<PreparedStatement> targetType() { return PreparedStatement.class; }
    @Override public Class<Object> valueType() { return Object.class; }

    @Override
    public boolean supports(BindContext ctx, Bind bind, Object encodedValue) {
      return bind != null && "json".equalsIgnoreCase(bind.userTypeId());
    }

    @Override
    public void bind(PreparedStatement ps, BindContext ctx, Bind bind, Object encodedValue, UserTypeRegistry userTypes) {
      if (!(ctx instanceof JdbcBindContext jc)) throw new IllegalArgumentException("Expected JdbcBindContext");
      int pos = jc.position1Based();
      try {
        if (encodedValue == null) {
          ps.setNull(pos, Types.OTHER);
          return;
        }
        PGobject obj = new PGobject();
        obj.setType("jsonb");
        obj.setValue(String.valueOf(encodedValue));
        ps.setObject(pos, obj);
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Bind list<*> as Postgres native arrays when supported.\n
   *
   * Notes:\n
   * - This binder is dialect-specific and will win over the global JDBC fallback binder\n
   *   that stores list<*> as JSON text.\n
   * - We keep the mapping table explicit to avoid surprises across dialects.\n
   */
  static final class PostgresArrayBinder implements Binder<PreparedStatement, List<?>> {
    @Override public Class<PreparedStatement> targetType() { return PreparedStatement.class; }
    @SuppressWarnings("unchecked")
    @Override public Class<List<?>> valueType() { return (Class<List<?>>) (Class<?>) List.class; }

    private static final Map<String, String> PG_ELEM_TYPES = Map.of(
        "list<string>", "text",
        "list<int>", "int4",
        "list<long>", "int8",
        "list<double>", "float8",
        "list<bool>", "bool",
        "list<uuid>", "uuid"
    );

    @Override
    public boolean supports(BindContext ctx, Bind bind, List<?> encodedValue) {
      if (bind == null) return false;
      String ut = bind.userTypeId();
      if (ut == null) return false;
      return PG_ELEM_TYPES.containsKey(ut.toLowerCase());
    }

    @Override
    public void bind(PreparedStatement ps, BindContext ctx, Bind bind, List<?> encodedValue, UserTypeRegistry userTypes) {
      if (!(ctx instanceof JdbcBindContext jc)) throw new IllegalArgumentException("Expected JdbcBindContext");
      int pos = jc.position1Based();
      try {
        if (encodedValue == null) {
          ps.setArray(pos, null);
          return;
        }
        String ut = bind.userTypeId();
        String pgElem = PG_ELEM_TYPES.get(ut == null ? null : ut.toLowerCase());
        if (pgElem == null) throw new IllegalArgumentException("Unsupported Postgres array userTypeId: " + ut);

        Object[] arr = encodedValue.toArray();
        Array sqlArr = ps.getConnection().createArrayOf(pgElem, arr);
        ps.setArray(pos, sqlArr);
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }
}


