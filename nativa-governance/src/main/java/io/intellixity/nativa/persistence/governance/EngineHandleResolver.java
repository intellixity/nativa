package io.intellixity.nativa.persistence.governance;

import io.intellixity.nativa.persistence.exec.handle.EngineHandle;

/**
 * Application-implemented resolver that maps (engineFamily, governance context, readOnly) to an {@link EngineHandle}.\n
 *
 * This is intended to be used by application code at engine construction time.\n
 */
public interface EngineHandleResolver {
  <T extends EngineHandle<?>> T resolve(String engineFamily, GovernanceContext ctx, boolean readOnly);
}




