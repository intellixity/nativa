package io.intellixity.nativa.persistence.authoring;

public interface AuthoringRegistry {
  EntityAuthoring getEntityAuthoring(String authoringId);
  ViewDef getViewDef(String viewDefId);
}

