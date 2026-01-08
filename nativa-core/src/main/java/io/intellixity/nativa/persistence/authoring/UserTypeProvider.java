package io.intellixity.nativa.persistence.authoring;

import java.util.Collection;

/**
 * Discovers {@link UserType} implementations.\n
 *
 * Resolution semantics:\n
 * - Provider is keyed by {@link #dialectId()}.\n
 * - {@code "*"} means global (fallback).\n
 * - For a given engine, dialect-specific types override global ones by id.\n
 */
public interface UserTypeProvider {
  /** Dialect id this provider targets, or "*" for global. */
  String dialectId();

  /** UserTypes contributed by this provider. */
  Collection<UserType<?>> userTypes();
}




