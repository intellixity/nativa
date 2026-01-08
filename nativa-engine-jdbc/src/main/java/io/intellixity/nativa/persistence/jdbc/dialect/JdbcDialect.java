package io.intellixity.nativa.persistence.jdbc.dialect;

import io.intellixity.nativa.persistence.jdbc.SqlStatement;
import io.intellixity.nativa.persistence.spi.sql.Dialect;

/** Dialect for JDBC engines (statement rendering only). */
public interface JdbcDialect extends Dialect<SqlStatement> {
}


