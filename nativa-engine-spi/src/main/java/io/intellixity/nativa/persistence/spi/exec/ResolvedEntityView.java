package io.intellixity.nativa.persistence.spi.exec;

import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.ViewDef;

/** Internal resolved pair for engine execution. */
record ResolvedEntityView(EntityAuthoring entityAuthoring, ViewDef viewDef) {
  ResolvedEntityView {
    if (entityAuthoring == null) throw new IllegalArgumentException("entityAuthoring is required");
    if (viewDef == null) throw new IllegalArgumentException("viewDef is required");
  }
}


