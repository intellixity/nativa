package io.intellixity.nativa.persistence.exec;

/**
 * Public selector for persistence operations.\n
 *
 * @param type logical entity type (authoring EntityAuthoring.type)\n
 * @param viewDefId view definition id\n
 */
public record EntityViewRef(String type, String viewDefId) {
  public EntityViewRef {
    if (type == null || type.isBlank()) throw new IllegalArgumentException("type is required");
    if (viewDefId == null || viewDefId.isBlank()) throw new IllegalArgumentException("viewDefId is required");
  }
}




