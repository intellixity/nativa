package io.intellixity.nativa.persistence.mongo.bind;

import io.intellixity.nativa.persistence.spi.bind.BindContext;

public interface MongoBindContext extends BindContext {
  String path();
}


