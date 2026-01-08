package io.intellixity.nativa.persistence.query;

public enum Operator {
  EQ(false),
  NE(false),
  GT(false),
  GE(false),
  LT(false),
  LE(false),

  IN(false),
  NIN(false),

  RANGE(false),
  LIKE(false),

  // Dialect-sensitive operators (treat as \"private\" so external/public callers can be restricted)
  ARRAY_CONTAINS(true),
  ARRAY_NOT_CONTAINS(true),
  ARRAY_OVERLAPS(true),
  ARRAY_NOT_OVERLAPS(true),
  JSON_PATH_EXISTS(true),
  JSON_VALUE_EQ(true);

  private final boolean isPrivate;

  Operator(boolean isPrivate) {
    this.isPrivate = isPrivate;
  }

  public boolean isPrivate() {
    return isPrivate;
  }
}

