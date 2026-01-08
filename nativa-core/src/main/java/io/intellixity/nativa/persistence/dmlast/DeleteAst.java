package io.intellixity.nativa.persistence.dmlast;

import io.intellixity.nativa.persistence.query.QueryElement;

public record DeleteAst(
    String table,
    QueryElement where
) implements DmlAst {
}



