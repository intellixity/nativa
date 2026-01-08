package io.intellixity.nativa.persistence.mongo;

import io.intellixity.nativa.persistence.authoring.ViewDef;
import io.intellixity.nativa.persistence.authoring.ViewMappings;
import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;
import io.intellixity.nativa.persistence.mapping.RowAdapter;
import io.intellixity.nativa.persistence.mapping.RowAdapters;

import java.util.Map;

/** RowAdapter that resolves property paths using {@link ViewMappings#ref(ViewDef, String)} for Mongo documents. */
final class MongoViewRowAdapter implements RowAdapter {
  private final RowAdapter base;
  private final ViewDef view;
  private final String prefix;

  MongoViewRowAdapter(RowAdapter base, ViewDef view) {
    this(base, view, "");
  }

  private MongoViewRowAdapter(RowAdapter base, ViewDef view, String prefix) {
    this.base = base;
    this.view = view;
    this.prefix = (prefix == null) ? "" : prefix;
  }

  @Override public UserTypeRegistry userTypes() { return base.userTypes(); }

  @Override
  public boolean isNull(String path) {
    return raw(path) == null;
  }

  @Override
  public Object raw(String path) {
    String fullPath = prefix + path;
    String ref = ViewMappings.ref(view, fullPath);
    String key = (ref == null || ref.isBlank()) ? fullPath : ref;
    return base.raw(key);
  }

  @Override
  public RowAdapter object(String pathOrPrefix) {
    String fullPath = prefix + pathOrPrefix;
    String mode = ViewMappings.mode(view, fullPath.endsWith(".") ? fullPath.substring(0, fullPath.length() - 1) : fullPath);
    if ("blob".equalsIgnoreCase(mode)) {
      Object raw = raw(pathOrPrefix.endsWith(".") ? pathOrPrefix.substring(0, pathOrPrefix.length() - 1) : pathOrPrefix);
      return RowAdapters.fromObject(raw, userTypes());
    }
    return new MongoViewRowAdapter(base, view, fullPath);
  }

  @Override
  public Iterable<Object> arrayRaw(String path) {
    return base.arrayRaw(resolve(prefix + path));
  }

  @Override
  public Map<String, Object> map(String path) {
    return base.map(resolve(prefix + path));
  }

  private String resolve(String fullPath) {
    String ref = ViewMappings.ref(view, fullPath);
    return (ref == null || ref.isBlank()) ? fullPath : ref;
  }
}





