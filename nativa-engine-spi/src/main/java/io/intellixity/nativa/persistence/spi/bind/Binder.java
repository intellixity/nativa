package io.intellixity.nativa.persistence.spi.bind;

import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;
import io.intellixity.nativa.persistence.compile.Bind;

/**
 * Generic binder that applies an encoded value into a native target.\n
 *
 * The value is already {@link io.intellixity.nativa.persistence.authoring.UserType#encode(Object)}'d.\n
 * Binder may further adapt it for driver/dialect specifics.\n
 */
public interface Binder<TTarget, TValue> {
  Class<TTarget> targetType();

  Class<TValue> valueType();

  boolean supports(BindContext ctx, Bind bind, TValue encodedValue);

  void bind(TTarget target, BindContext ctx, Bind bind, TValue encodedValue, UserTypeRegistry userTypes);
}


