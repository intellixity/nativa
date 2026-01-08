package io.intellixity.nativa.persistence.pojo;

/** Write-side accessor for generated mutable POJOs. */
public interface PojoMutator<T> {
  /**
   * Set a top-level field by name.\n
   *
   * Implementations are generated (no reflection).\n
   */
  void set(T pojo, String field, Object value);
}




