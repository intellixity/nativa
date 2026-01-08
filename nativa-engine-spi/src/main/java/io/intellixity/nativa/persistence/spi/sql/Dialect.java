package io.intellixity.nativa.persistence.spi.sql;

import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.ViewDef;
import io.intellixity.nativa.persistence.compile.PropertyTypeResolver;
import io.intellixity.nativa.persistence.dmlast.DmlAst;
import io.intellixity.nativa.persistence.query.Page;
import io.intellixity.nativa.persistence.query.QueryElement;
import io.intellixity.nativa.persistence.query.SortField;

import java.util.Map;
import java.util.List;

/** Backend-agnostic SPI: merges view-provided base native query with filter/sort/page and renders DML. */
public interface Dialect<S extends NativeStatement> {
  String id();

  S mergeSelect(EntityAuthoring ea, ViewDef view, QueryElement filter,
                List<SortField> sort, Page page, Map<String, Object> params,
                PropertyTypeResolver types);

  S mergeCount(EntityAuthoring ea, ViewDef view, QueryElement filter, Map<String, Object> params,
               PropertyTypeResolver types);

  S renderDml(EntityAuthoring ea, ViewDef view, DmlAst dml, PropertyTypeResolver types);
}


