package io.intellixity.nativa.persistence.jdbc.bind;

import io.intellixity.nativa.persistence.authoring.UserType;
import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;
import io.intellixity.nativa.persistence.spi.bind.*;
import io.intellixity.nativa.persistence.compile.Bind;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * JDBC-family binder base.\n
 *
 * Dialect providers (e.g. postgres, mssql) should extend this and add dialect binders.\n
 * Dialect binders are evaluated before base JDBC binders.\n
 */
public abstract class JdbcBinderProvider implements BinderProvider {
  @Override
  public final Collection<Binder<?, ?>> binders() {
    List<Binder<?, ?>> out = new ArrayList<>();
    out.addAll(dialectBinders());
    out.addAll(jdbcBinders());
    return List.copyOf(out);
  }

  /** Dialect-specific binders (default empty). Put overriding binders here. */
  protected Collection<Binder<?, ?>> dialectBinders() {
    return Collections.emptyList();
  }

  /** Base JDBC binders shared by all JDBC dialects. */
  protected Collection<Binder<?, ?>> jdbcBinders() {
    return List.of(
        new JdbcInstantToTimestampBinder(),
        new JdbcListAsJsonTextBinder(),
        new JdbcSetObjectBinder()
    );
  }

  /** Bind Instant as JDBC Timestamp for all JDBC dialects (backend-specific behavior, not global UserType behavior). */
  static final class JdbcInstantToTimestampBinder implements Binder<PreparedStatement, Instant> {
    @Override public Class<PreparedStatement> targetType() { return PreparedStatement.class; }
    @Override public Class<Instant> valueType() { return Instant.class; }

    @Override
    public boolean supports(BindContext ctx, Bind bind, Instant encodedValue) {
      return bind != null && "instant".equalsIgnoreCase(bind.userTypeId());
    }

    @Override
    public void bind(PreparedStatement ps, BindContext ctx, Bind bind, Instant encodedValue, UserTypeRegistry userTypes) {
      if (!(ctx instanceof JdbcBindContext jc)) throw new IllegalArgumentException("Expected JdbcBindContext");
      int pos = jc.position1Based();
      try {
        if (encodedValue == null) {
          ps.setTimestamp(pos, null);
        } else {
          ps.setTimestamp(pos, Timestamp.from(encodedValue));
        }
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }

  static final class JdbcSetObjectBinder implements Binder<PreparedStatement, Object> {
    @Override public Class<PreparedStatement> targetType() { return PreparedStatement.class; }
    @Override public Class<Object> valueType() { return Object.class; }
    @Override public boolean supports(BindContext ctx, Bind bind, Object encodedValue) { return true; }

    @Override
    public void bind(PreparedStatement ps, BindContext ctx, Bind bind, Object encodedValue, UserTypeRegistry userTypes) {
      if (!(ctx instanceof JdbcBindContext jc)) throw new IllegalArgumentException("Expected JdbcBindContext");
      int pos = jc.position1Based();
      try {
        ps.setObject(pos, encodedValue);
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /** For JDBC dialects without native array binding, store list<*> as JSON text. */
  static final class JdbcListAsJsonTextBinder implements Binder<PreparedStatement, List<?>> {
    @Override public Class<PreparedStatement> targetType() { return PreparedStatement.class; }
    @SuppressWarnings("unchecked")
    @Override public Class<List<?>> valueType() { return (Class<List<?>>) (Class<?>) List.class; }

    @Override
    public boolean supports(BindContext ctx, Bind bind, List<?> encodedValue) {
      if (bind == null) return false;
      String ut = bind.userTypeId();
      if (ut == null) return false;
      return ut.startsWith("list<") && ut.endsWith(">");
    }

    @Override
    public void bind(PreparedStatement ps, BindContext ctx, Bind bind, List<?> encodedValue, UserTypeRegistry userTypes) {
      // reuse json usertype encoding so JSON rules are centralized
      @SuppressWarnings("unchecked")
      UserType<Object> json = (UserType<Object>) userTypes.get("json");
      Object jsonText = json.encode(encodedValue);
      new JdbcSetObjectBinder().bind(ps, ctx, bind, jsonText, userTypes);
    }
  }
}


