package io.intellixity.nativa.examples.engine;

import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.governance.Governance;
import io.intellixity.nativa.persistence.governance.GovernanceContext;
import io.intellixity.nativa.persistence.governance.GovernanceDataEngineResolver;
import io.intellixity.nativa.persistence.jdbc.JdbcHandle;

public final class Engines {
  private final GovernanceDataEngineResolver resolver;

  public Engines(GovernanceDataEngineResolver resolver) {
    this.resolver = resolver;
  }

  public DataEngine<JdbcHandle> jdbc(boolean readOnly) {
    GovernanceContext ctx = Governance.currentOrThrow();
    @SuppressWarnings("unchecked")
    DataEngine<JdbcHandle> e = (DataEngine<JdbcHandle>) resolver.resolve("jdbc", ctx, readOnly);
    return e;
  }
}




