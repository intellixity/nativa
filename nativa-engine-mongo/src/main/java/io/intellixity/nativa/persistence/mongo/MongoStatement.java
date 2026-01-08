package io.intellixity.nativa.persistence.mongo;

import org.bson.Document;
import io.intellixity.nativa.persistence.dmlast.DmlAst;
import io.intellixity.nativa.persistence.query.QueryElement;
import io.intellixity.nativa.persistence.spi.sql.NativeStatement;

import java.util.List;

/** Backend-native statement representation for MongoDB. */
public record MongoStatement(
    Kind kind,
    String collection,
    Document filter,
    QueryElement filterExpr,
    List<Document> pipeline,
    Document sort,
    Integer skip,
    Integer limit,
    DmlAst dml,
    boolean upsert
) implements NativeStatement {
  public enum Kind {
    FIND,
    COUNT,
    INSERT_ONE,
    INSERT_MANY,
    UPDATE_ONE,
    UPDATE_MANY,
    DELETE_MANY,
    AGGREGATE
  }
}
