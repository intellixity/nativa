package io.intellixity.nativa.persistence.mapping;

import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;

import java.util.Map;

/** RowAdapter that remaps property paths to result-set labels (for query/projection views). */
public final class MappedRowAdapter implements RowAdapter {
  private final RowAdapter base;
  private final Map<String, String> labelsByPath;
  private final String prefix;

  public MappedRowAdapter(RowAdapter base, Map<String, String> labelsByPath) {
    this(base, labelsByPath, "");
  }

  private MappedRowAdapter(RowAdapter base, Map<String, String> labelsByPath, String prefix) {
    this.base = base;
    this.labelsByPath = labelsByPath == null ? Map.of() : labelsByPath;
    this.prefix = prefix == null ? "" : prefix;
  }

  @Override
  public UserTypeRegistry userTypes() {
    return base.userTypes();
  }

  @Override
  public boolean isNull(String path) {
    return base.isNull(labelFor(path));
  }

  @Override
  public Object raw(String path) {
    return base.raw(labelFor(path));
  }

  @Override
  public RowAdapter object(String pathOrPrefix) {
    String p = pathOrPrefix == null ? "" : pathOrPrefix;
    return new MappedRowAdapter(base, labelsByPath, prefix + p);
  }

  @Override
  public Iterable<Object> arrayRaw(String path) {
    return base.arrayRaw(labelFor(path));
  }

  @Override
  public Map<String, Object> map(String path) {
    return base.map(labelFor(path));
  }

  private String labelFor(String path) {
    String p = path == null ? "" : path;
    String full = prefix + p;
    return labelsByPath.getOrDefault(full, full);
  }
}





