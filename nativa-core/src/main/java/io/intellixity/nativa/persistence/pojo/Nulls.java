package io.intellixity.nativa.persistence.pojo;

import java.util.Collection;

/**
 * Explicit null markers for DML.\n
 *
 * <p>Generated POJOs can implement this so callers can express intent:\n
 * setting a column to NULL vs leaving it unset.</p>
 *
 * <p>For now, field names are top-level only (e.g. "status").</p>
 */
public interface Nulls {
  Collection<String> getNulls();
}




