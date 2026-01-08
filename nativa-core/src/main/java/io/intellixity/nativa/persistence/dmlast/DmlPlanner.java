package io.intellixity.nativa.persistence.dmlast;

import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.ViewDef;
import io.intellixity.nativa.persistence.query.QueryElement;

/** Backend-family planner that maps entity+view+pojo into a backend-agnostic DML AST. */
public interface DmlPlanner {
  InsertAst planInsert(EntityAuthoring ea, ViewDef view, Object pojo, boolean returningKey);

  UpdateAst planUpdateById(EntityAuthoring ea, ViewDef view, Object pojo);

  UpdateAst planUpdateByCriteria(EntityAuthoring ea, ViewDef view, Object pojo, QueryElement where);

  DeleteAst planDeleteByCriteria(EntityAuthoring ea, ViewDef view, QueryElement where);

  UpsertAst planUpsert(EntityAuthoring ea, ViewDef view, Object pojo, boolean returningKey);
}


