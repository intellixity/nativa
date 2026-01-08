package io.intellixity.nativa.persistence.exec;

/**
 * Transaction propagation behavior for {@link DataEngine#inTx(Propagation, java.util.function.Supplier)}.
 * <p>
 * This is inspired by Spring's propagation model, but implemented without any Spring dependency.
 */
public enum Propagation {
  /** Support a current transaction, create a new one if none exists. */
  REQUIRED,

  /** Support a current transaction, execute non-transactionally if none exists. */
  SUPPORTS,

  /** Support a current transaction, throw an exception if none exists. */
  MANDATORY,

  /** Create a new transaction for the work. (Best-effort: no true suspension required with ScopedValue scoping.) */
  REQUIRES_NEW,

  /** Execute non-transactionally, throw an exception if a transaction exists. */
  NEVER,

  /** Best-effort nested behavior; currently treated like {@link #REQUIRED}. */
  NESTED
}


