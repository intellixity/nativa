package io.intellixity.nativa.persistence.query;

import java.util.Objects;

public record SortField(String field, Direction direction) {
  public SortField {
    Objects.requireNonNull(field, "field");
    direction = (direction == null) ? Direction.ASC : direction;
  }

  public enum Direction { ASC, DESC }
}

