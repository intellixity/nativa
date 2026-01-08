package io.intellixity.nativa.persistence.jdbc.bind;

import io.intellixity.nativa.persistence.spi.bind.DiscoveredBinderRegistry;

/**
 * Global JDBC provider discovered via META-INF/nativa.factories.\n
 *
 * Supplies base JDBC binders from {@link JdbcBinderProvider} for all dialects.\n
 */
public final class DefaultJdbcBinderProvider extends JdbcBinderProvider {
  @Override
  public String dialectId() {
    return DiscoveredBinderRegistry.GLOBAL_DIALECT;
  }
}


