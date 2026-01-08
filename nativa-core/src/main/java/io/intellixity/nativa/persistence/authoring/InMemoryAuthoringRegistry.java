package io.intellixity.nativa.persistence.authoring;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple in-memory {@link ListableAuthoringRegistry}.\n
 *
 * Useful for tests, demos, and codegen tooling.\n
 */
public final class InMemoryAuthoringRegistry implements ListableAuthoringRegistry {
  private final Map<String, EntityAuthoring> entities = new HashMap<>();
  private final Map<String, ViewDef> views = new HashMap<>();
  private final Map<String, String> viewToEntity = new HashMap<>();

  public InMemoryAuthoringRegistry(List<EntityAuthoring> eas) {
    for (EntityAuthoring ea : eas) {
      entities.put(ea.type(), ea);
      if (ea.views() != null) {
        for (ViewDef v : ea.views().values()) {
          views.put(v.id(), v);
          viewToEntity.put(v.id(), ea.type());
        }
      }
    }
  }

  @Override
  public EntityAuthoring getEntityAuthoring(String authoringId) {
    EntityAuthoring ea = entities.get(authoringId);
    if (ea == null) throw new IllegalArgumentException("Unknown authoring type: " + authoringId);
    return ea;
  }

  @Override
  public ViewDef getViewDef(String viewDefId) {
    ViewDef v = views.get(viewDefId);
    if (v == null) throw new IllegalArgumentException("Unknown view: " + viewDefId);
    return v;
  }

  @Override
  public Collection<EntityAuthoring> allEntities() { return entities.values(); }

  @Override
  public Collection<ViewDef> allViews() { return views.values(); }

  @Override
  public EntityAuthoring entityForView(String viewId) {
    String eid = viewToEntity.get(viewId);
    if (eid == null) throw new IllegalArgumentException("Unknown view->entity mapping: " + viewId);
    return getEntityAuthoring(eid);
  }
}




