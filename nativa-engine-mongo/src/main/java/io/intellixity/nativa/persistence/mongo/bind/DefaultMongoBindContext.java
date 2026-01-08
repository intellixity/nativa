package io.intellixity.nativa.persistence.mongo.bind;

import io.intellixity.nativa.persistence.spi.bind.BindOpKind;

public record DefaultMongoBindContext(
    BindOpKind opKind,
    String path
) implements MongoBindContext {
  public DefaultMongoBindContext {
    if (opKind == null) throw new IllegalArgumentException("opKind is required");
    if (path == null || path.isBlank()) throw new IllegalArgumentException("path is required");
  }
}


