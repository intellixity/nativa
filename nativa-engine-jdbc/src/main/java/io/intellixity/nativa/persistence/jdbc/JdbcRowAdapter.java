package io.intellixity.nativa.persistence.jdbc;

import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;
import io.intellixity.nativa.persistence.mapping.RowAdapter;

import java.sql.*;
import java.util.*;

public final class JdbcRowAdapter implements RowAdapter {
  private final ResultSet rs;
  private final UserTypeRegistry userTypes;
  private Map<String, Integer> colIndex;

  public JdbcRowAdapter(ResultSet rs, UserTypeRegistry userTypes) {
    this.rs = rs;
    this.userTypes = userTypes;
  }

  @Override public UserTypeRegistry userTypes() { return userTypes; }
  @Override public boolean isNull(String path) { return raw(path) == null; }

  @Override
  public Object raw(String path) {
    try {
      int idx = indexOf(path);
      return rs.getObject(idx);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public RowAdapter object(String prefix) {
    return new PrefixedJdbcRowAdapter(this, prefix);
  }

  @Override
  public Iterable<Object> arrayRaw(String path) {
    Object v = raw(path);
    if (v == null) return null;
    try {
      if (v instanceof java.sql.Array a) {
        Object arr = a.getArray();
        if (arr instanceof Object[] oa) return Arrays.asList(oa);
      }
      if (v instanceof Object[] oa) return Arrays.asList(oa);
      if (v instanceof List<?> l) {
        @SuppressWarnings("unchecked")
        List<Object> lo = (List<Object>) l;
        return lo;
      }
      throw new IllegalArgumentException("Not an array: " + v.getClass());
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Map<String, Object> map(String path) {
    Object v = raw(path);
    if (v == null) return null;
    if (v instanceof Map<?,?> m) {
      @SuppressWarnings("unchecked")
      Map<String,Object> mm = (Map<String,Object>) m;
      return mm;
    }
    // Decode JSON string/object into a Map using the global json UserType.
    Object decoded;
    try {
      decoded = userTypes.get("json").decode((v instanceof String) ? v : String.valueOf(v));
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to decode map() JSON at path='" + path + "'", e);
    }
    if (decoded == null) return null;
    if (decoded instanceof Map<?, ?> dm) {
      @SuppressWarnings("unchecked")
      Map<String, Object> out = (Map<String, Object>) dm;
      return out;
    }
    throw new IllegalArgumentException("Not a JSON object map at path='" + path + "': " + decoded.getClass().getName());
  }

  private int indexOf(String label) throws SQLException {
    if (colIndex == null) {
      colIndex = new HashMap<>();
      ResultSetMetaData md = rs.getMetaData();
      for (int i = 1; i <= md.getColumnCount(); i++) {
        colIndex.put(md.getColumnLabel(i), i);
      }
    }
    Integer i = colIndex.get(label);
    if (i == null) throw new IllegalArgumentException("Unknown column label: " + label);
    return i;
  }

  static final class PrefixedJdbcRowAdapter implements RowAdapter {
    private final RowAdapter base;
    private final String prefix;

    PrefixedJdbcRowAdapter(RowAdapter base, String prefix) {
      this.base = base;
      this.prefix = prefix;
    }

    @Override public UserTypeRegistry userTypes() { return base.userTypes(); }
    @Override public boolean isNull(String path) { return base.isNull(prefix + path); }
    @Override public Object raw(String path) { return base.raw(prefix + path); }
    @Override public RowAdapter object(String p) { return base.object(prefix + p); }
    @Override public Iterable<Object> arrayRaw(String path) { return base.arrayRaw(prefix + path); }
    @Override public Map<String, Object> map(String path) { return base.map(prefix + path); }
  }
}

