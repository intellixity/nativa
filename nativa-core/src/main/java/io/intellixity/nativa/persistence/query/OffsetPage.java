package io.intellixity.nativa.persistence.query;

public record OffsetPage(int offset, int limit) implements Page {
  public OffsetPage {
    if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
    if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
  }
}

