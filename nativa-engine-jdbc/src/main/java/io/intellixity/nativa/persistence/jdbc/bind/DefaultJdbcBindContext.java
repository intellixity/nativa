package io.intellixity.nativa.persistence.jdbc.bind;

import io.intellixity.nativa.persistence.spi.bind.BindOpKind;

public record DefaultJdbcBindContext(
    BindOpKind opKind,
    int position1Based
) implements JdbcBindContext {
  public DefaultJdbcBindContext {
    if (opKind == null) throw new IllegalArgumentException("opKind is required");
    if (position1Based <= 0) throw new IllegalArgumentException("position1Based must be >= 1");
  }
}


