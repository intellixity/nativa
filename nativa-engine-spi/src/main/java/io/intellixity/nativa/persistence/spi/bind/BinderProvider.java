package io.intellixity.nativa.persistence.spi.bind;

import java.util.Collection;

/** Discovers {@link Binder} implementations, keyed by dialect id. */
public interface BinderProvider {
  /** Dialect id this provider targets, or "*" for global. */
  String dialectId();

  Collection<Binder<?, ?>> binders();
}


