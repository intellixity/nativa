package io.intellixity.nativa.persistence.query;

import java.util.Map;

/**
 * Seek pagination (a.k.a. keyset pagination).\n
 *
 * <p>Requires a cursor map ("after") carrying the last-seen values for the active sort fields.\n
 * Dialects use it to build a lexicographic predicate.</p>
 */
public record SeekPage(int limit, Map<String, Object> after) implements Page {
  public SeekPage {
    if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
    after = (after == null) ? Map.of() : Map.copyOf(after);
  }
}

