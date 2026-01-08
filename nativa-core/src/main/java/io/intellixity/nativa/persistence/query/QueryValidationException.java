package io.intellixity.nativa.persistence.query;

/**
 * Raised when a Query references invalid/unknown fields or otherwise fails validation.
 * <p>
 * Intended to be thrown by backend-agnostic validation (and optionally by backends as a safety net).
 */
public final class QueryValidationException extends RuntimeException {
  public QueryValidationException(String message) {
    super(message);
  }

  public QueryValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}


