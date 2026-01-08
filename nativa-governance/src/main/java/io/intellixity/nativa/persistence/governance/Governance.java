package io.intellixity.nativa.persistence.governance;

import java.lang.ScopedValue;
import java.util.Objects;
import java.util.function.Supplier;

/** Scoped governance context utilities (Java 25 ScopedValue). */
public final class Governance {
  private Governance() {}

  private static final ScopedValue<GovernanceContext> CTX = ScopedValue.newInstance();

  /** Execute work within a governance context boundary. */
  public static <T> T inContext(GovernanceContext ctx, Supplier<T> work) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(work, "work");
    try {
      return ScopedValue.where(CTX, ctx).call(work::get);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static GovernanceContext currentOrNull() {
    return CTX.isBound() ? CTX.get() : null;
  }

  public static GovernanceContext currentOrThrow() {
    GovernanceContext c = currentOrNull();
    if (c == null) throw new IllegalStateException("No GovernanceContext bound in current scope");
    return c;
  }
}




