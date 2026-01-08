package io.intellixity.nativa.persistence.mapping;

import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;

import java.util.Map;

/** RowAdapter that treats object(...) as a prefix concatenation (works for dot-path conventions). */
public final class PrefixedRowAdapter implements RowAdapter {
  private final RowAdapter base;
  private final String prefix;

  public PrefixedRowAdapter(RowAdapter base, String prefix) {
    this.base = base;
    this.prefix = prefix == null ? "" : prefix;
  }

  @Override public UserTypeRegistry userTypes() { return base.userTypes(); }
  @Override public boolean isNull(String path) { return base.isNull(prefix + (path == null ? "" : path)); }
  @Override public Object raw(String path) { return base.raw(prefix + (path == null ? "" : path)); }

  @Override
  public RowAdapter object(String pathOrPrefix) {
    String p = pathOrPrefix == null ? "" : pathOrPrefix;
    return new PrefixedRowAdapter(base, prefix + p);
  }

  @Override public Iterable<Object> arrayRaw(String path) { return base.arrayRaw(prefix + (path == null ? "" : path)); }
  @Override public Map<String, Object> map(String path) { return base.map(prefix + (path == null ? "" : path)); }
}





