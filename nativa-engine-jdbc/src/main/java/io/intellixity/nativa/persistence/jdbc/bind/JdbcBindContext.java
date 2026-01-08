package io.intellixity.nativa.persistence.jdbc.bind;

import io.intellixity.nativa.persistence.spi.bind.BindContext;

public interface JdbcBindContext extends BindContext {
  int position1Based();
}


