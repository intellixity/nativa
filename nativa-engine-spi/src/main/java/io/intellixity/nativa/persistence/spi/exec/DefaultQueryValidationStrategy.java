package io.intellixity.nativa.persistence.spi.exec;

import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.ViewDef;
import io.intellixity.nativa.persistence.compile.PropertyTypeResolver;
import io.intellixity.nativa.persistence.query.*;
import io.intellixity.nativa.persistence.query.aggregation.GroupBy;

import java.util.List;
import java.util.Objects;

/**
 * Default, backend-agnostic query validation.\n
 *
 * Validates:\n
 * - filter Condition.property paths\n
 * - sort fields\n
 * - groupBy fields\n
 *
 * Unknown scalar field paths throw {@link QueryValidationException}.\n
 */
public final class DefaultQueryValidationStrategy implements QueryValidationStrategy {
  @Override
  public void validate(EntityAuthoring entity,
                       ViewDef view,
                       Query effectiveQuery,
                       QueryElement normalizedFilter,
                       PropertyTypeResolver types) {
    Objects.requireNonNull(entity, "entity");
    Objects.requireNonNull(types, "types");

    validateElement(entity, normalizedFilter, types);

    if (effectiveQuery != null) {
      validateSort(entity, effectiveQuery.sort(), types);
      validateGroupBy(entity, effectiveQuery.groupBy(), types);
    }
  }

  private static void validateSort(EntityAuthoring ea, List<SortField> sort, PropertyTypeResolver types) {
    if (sort == null || sort.isEmpty()) return;
    for (SortField sf : sort) {
      if (sf == null) continue;
      requireScalarPath(ea, sf.field(), types, "sort");
    }
  }

  private static void validateGroupBy(EntityAuthoring ea, GroupBy groupBy, PropertyTypeResolver types) {
    if (groupBy == null || groupBy.fields() == null || groupBy.fields().isEmpty()) return;
    for (String f : groupBy.fields()) {
      requireScalarPath(ea, f, types, "groupBy");
    }
  }

  private static void validateElement(EntityAuthoring ea, QueryElement el, PropertyTypeResolver types) {
    if (el == null) return;

    if (el instanceof Query q) {
      validateElement(ea, q.filter(), types);
      return;
    }
    if (el instanceof NotElement n) {
      validateElement(ea, n.element(), types);
      return;
    }
    if (el instanceof LogicalGroup g) {
      if (g.elements() == null) return;
      for (QueryElement c : g.elements()) validateElement(ea, c, types);
      return;
    }
    if (el instanceof Condition c) {
      requireScalarPath(ea, c.property(), types, "filter");
      return;
    }

    throw new QueryValidationException("Unsupported QueryElement: " + el.getClass().getName());
  }

  private static void requireScalarPath(EntityAuthoring ea,
                                        String propertyPath,
                                        PropertyTypeResolver types,
                                        String usage) {
    if (propertyPath == null || propertyPath.isBlank()) {
      throw new QueryValidationException("Blank propertyPath in " + usage + " for entity '" + ea.type() + "'");
    }
    String userTypeId = types.resolveScalarUserTypeId(ea, propertyPath);
    if (userTypeId == null) {
      throw new QueryValidationException(
          "Unknown scalar field path '" + propertyPath + "' in " + usage + " for entity '" + ea.type() + "'"
      );
    }
  }
}


