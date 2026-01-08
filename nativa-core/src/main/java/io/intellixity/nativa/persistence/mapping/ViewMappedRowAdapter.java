package io.intellixity.nativa.persistence.mapping;

import io.intellixity.nativa.persistence.authoring.ViewDef;
import io.intellixity.nativa.persistence.authoring.ViewMappings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RowAdapter that:\n
 * - Remaps property paths to result-set labels (via view.mapping label).\n
 * - Supports dot-path nested objects (object(\"customer.\") prefixes).\n
 * - Supports blob mode: if mapping for \"customer\" has mode=blob, then object(\"customer.\")\n
 *   materializes a nested adapter from the JSON/blob column \"customer\".\n
 */
public final class ViewMappedRowAdapter implements RowAdapter {
  private final RowAdapter base;
  private final ViewDef view;
  private final Map<String, String> labelsByPath;
  private final String prefix;

  // Cache decoded blob objects per root path (e.g. \"customer\").\n
  private final Map<String, RowAdapter> blobObjectCache;

  public ViewMappedRowAdapter(RowAdapter base, ViewDef view) {
    this(base, view, ViewMappings.labels(view), "", new ConcurrentHashMap<>());
  }

  private ViewMappedRowAdapter(RowAdapter base, ViewDef view, Map<String, String> labelsByPath,
                               String prefix, Map<String, RowAdapter> blobObjectCache) {
    this.base = base;
    this.view = view;
    this.labelsByPath = labelsByPath;
    this.prefix = prefix == null ? "" : prefix;
    this.blobObjectCache = blobObjectCache;
  }

  @Override
  public io.intellixity.nativa.persistence.authoring.UserTypeRegistry userTypes() {
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
    String nextPrefix = prefix + p;

    // Detect \"customer.\" -> rootPath \"customer\".\n
    String rootPath = stripTrailingDot(nextPrefix);
    if (!rootPath.isEmpty() && "blob".equalsIgnoreCase(ViewMappings.mode(view, rootPath))) {
      return blobObjectCache.computeIfAbsent(rootPath, rp -> {
        String label = labelFor(rp);
        Object decoded = base.decode(label, "json");
        if (!(decoded instanceof Map<?, ?>)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) decoded;
        // Use RowAdapters + PrefixedRowAdapter so nested object(\"address.\") works.\n
        return new PrefixedRowAdapter(RowAdapters.fromMap(m, userTypes()), "");
      });
    }

    return new ViewMappedRowAdapter(base, view, labelsByPath, nextPrefix, blobObjectCache);
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
    String mapped = labelsByPath.get(full);
    if (mapped != null && !mapped.equals(full)) return mapped;

    String ref = ViewMappings.ref(view, full);
    if (ref == null || ref.isBlank()) return full;
    return deriveLabelFromRef(ref, full);
  }

  private static String stripTrailingDot(String p) {
    if (p == null) return "";
    if (p.endsWith(".")) return p.substring(0, p.length() - 1);
    return p;
  }

  private static String deriveLabelFromRef(String ref, String fallback) {
    // If ref is an expression, we can't reliably infer label without explicit alias.
    // Heuristic: for `a.col` use `col`; for `col` use `col`.
    String r = ref.trim();
    // common cases: "t.col", "col"
    int dot = r.lastIndexOf('.');
    String out = (dot >= 0 && dot + 1 < r.length()) ? r.substring(dot + 1) : r;
    // If we still have whitespace/parens, give up and use fallback.
    for (int i = 0; i < out.length(); i++) {
      char c = out.charAt(i);
      if (Character.isWhitespace(c) || c == '(' || c == ')') return fallback;
    }
    return out;
  }
}




