package io.intellixity.nativa.persistence.authoring;

import java.util.Map;

public record ViewDef(
    String id,
    Map<String, Object> mapping,
    SqlViewDef sqlView
) {
  public ViewDef {
    mapping = mapping == null ? Map.of() : Map.copyOf(mapping);
  }
}

