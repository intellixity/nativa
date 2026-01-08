package io.intellixity.nativa.persistence.authoring;

import java.util.Map;

public record ScalarTypeRef(
    String userTypeId,
    Map<String, Object> attrs
) implements TypeRef {
  public ScalarTypeRef {
    attrs = attrs == null ? Map.of() : Map.copyOf(attrs);
  }
}

