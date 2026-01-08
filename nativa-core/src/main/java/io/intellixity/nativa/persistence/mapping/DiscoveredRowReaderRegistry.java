package io.intellixity.nativa.persistence.mapping;

import io.intellixity.nativa.persistence.util.NativaFactoriesLoader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DiscoveredRowReaderRegistry {
  private final Map<String, RowReader<?>> byType;

  public DiscoveredRowReaderRegistry() {
    this(NativaFactoriesLoader.load(RowReaderProvider.class));
  }

  DiscoveredRowReaderRegistry(List<RowReaderProvider> providers) {
    Map<String, RowReader<?>> out = new HashMap<>();
    for (RowReaderProvider p : providers) {
      if (p == null) continue;
      Map<String, RowReader<?>> m = p.rowReadersByType();
      if (m == null) continue;
      for (var e : m.entrySet()) {
        String type = (e.getKey() == null) ? null : e.getKey().trim();
        if (type == null || type.isEmpty()) continue;
        RowReader<?> rr = e.getValue();
        if (rr == null) continue;
        if (out.containsKey(type)) {
          throw new IllegalArgumentException("Duplicate RowReader for type '" + type + "' from providers. Existing=" +
              out.get(type).getClass().getName() + ", new=" + rr.getClass().getName());
        }
        out.put(type, rr);
      }
    }
    this.byType = Map.copyOf(out);
  }

  public RowReader<?> get(String type) {
    Objects.requireNonNull(type, "type");
    RowReader<?> rr = byType.get(type);
    if (rr == null) throw new IllegalArgumentException("No RowReader registered for type: " + type);
    return rr;
  }
}




