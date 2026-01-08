package io.intellixity.nativa.persistence.spi.exec;

import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.ViewDef;
import io.intellixity.nativa.persistence.compile.PropertyTypeResolver;
import io.intellixity.nativa.persistence.query.Query;
import io.intellixity.nativa.persistence.query.QueryElement;

/**
 * SPI hook to validate queries before backend compilation/execution.
 * <p>
 * Engines call this after {@link io.intellixity.nativa.persistence.compile.QueryNormalizer} normalization and before
 * dialect rendering. Applications/backends may plug in stricter rules or additional validation.
 */
public interface QueryValidationStrategy {
  void validate(EntityAuthoring entity,
                ViewDef view,
                Query effectiveQuery,
                QueryElement normalizedFilter,
                PropertyTypeResolver types);
}


