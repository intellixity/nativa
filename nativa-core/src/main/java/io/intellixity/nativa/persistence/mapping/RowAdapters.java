package io.intellixity.nativa.persistence.mapping;

import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;

import java.util.*;

public final class RowAdapters {
  private RowAdapters() {}

  public static RowAdapter fromMap(Map<String,Object> map, UserTypeRegistry userTypes) {
    return new MapRowAdapter(map, userTypes, "");
  }

  @SuppressWarnings("unchecked")
  public static RowAdapter fromObject(Object raw, UserTypeRegistry userTypes) {
    if (raw == null) return null;
    if (raw instanceof Map<?,?> m) return fromMap((Map<String,Object>) m, userTypes);
    throw new IllegalArgumentException("Expected Map for nested object but got: " + raw.getClass());
  }

  public static RowAdapter mapped(RowAdapter base, Map<String,String> pathMap) {
    return new MappedRowAdapter(base, pathMap);
  }

  static Object getByPath(Map<String,Object> root, String path) {
    if (path == null || path.isBlank()) return root;
    Object cur = root;
    for (String p : path.split("\\.")) {
      if (!(cur instanceof Map<?,?> m)) return null;
      cur = m.get(p);
    }
    return cur;
  }

  static final class MapRowAdapter implements RowAdapter {
    private final Map<String,Object> root;
    private final UserTypeRegistry userTypes;
    private final String prefix;

    MapRowAdapter(Map<String,Object> root, UserTypeRegistry userTypes, String prefix) {
      this.root = root;
      this.userTypes = userTypes;
      this.prefix = prefix == null ? "" : prefix;
    }

    @Override public UserTypeRegistry userTypes() { return userTypes; }
    @Override public boolean isNull(String path) { return raw(path) == null; }

    @Override
    public Object raw(String path) {
      return getByPath(root, prefix + path);
    }

    @Override
    public RowAdapter object(String pathOrPrefix) {
      Object v = raw(pathOrPrefix);
      @SuppressWarnings("unchecked")
      Map<String,Object> m = (v instanceof Map<?,?> mm) ? (Map<String,Object>) mm : null;
      return m == null ? null : new MapRowAdapter(m, userTypes, "");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterable<Object> arrayRaw(String path) {
      Object v = raw(path);
      if (v == null) return null;
      if (v instanceof List<?> l) return (List<Object>) l;
      if (v instanceof Object[] a) return Arrays.asList(a);
      throw new IllegalArgumentException("Not an array: " + v.getClass());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> map(String path) {
      Object v = raw(path);
      if (v == null) return null;
      if (v instanceof Map<?,?> m) return (Map<String,Object>) m;
      throw new IllegalArgumentException("Not a map: " + v.getClass());
    }
  }

  static final class MappedRowAdapter implements RowAdapter {
    private final RowAdapter base;
    private final Map<String,String> map;

    MappedRowAdapter(RowAdapter base, Map<String,String> map) {
      this.base = base;
      this.map = map;
    }

    @Override public UserTypeRegistry userTypes() { return base.userTypes(); }

    private String x(String path) {
      String m = map.get(path);
      return m != null ? m : path;
    }

    @Override public boolean isNull(String path) { return base.isNull(x(path)); }
    @Override public Object raw(String path) { return base.raw(x(path)); }
    @Override public RowAdapter object(String pathOrPrefix) { return base.object(x(pathOrPrefix)); }
    @Override public Iterable<Object> arrayRaw(String path) { return base.arrayRaw(x(path)); }
    @Override public Map<String, Object> map(String path) { return base.map(x(path)); }
  }
}

