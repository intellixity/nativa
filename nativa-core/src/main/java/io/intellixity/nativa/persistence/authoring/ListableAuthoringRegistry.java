package io.intellixity.nativa.persistence.authoring;

import java.util.Collection;

public interface ListableAuthoringRegistry extends AuthoringRegistry {
  Collection<EntityAuthoring> allEntities();
  Collection<ViewDef> allViews();
  EntityAuthoring entityForView(String viewId);
}

