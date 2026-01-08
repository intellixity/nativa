package io.intellixity.nativa.persistence.exec;

import io.intellixity.nativa.persistence.exec.handle.EngineHandle;
import io.intellixity.nativa.persistence.query.Query;

import java.util.List;
import java.util.function.Supplier;

public interface DataEngine<H extends EngineHandle<?>> {
  /** Returns the engine handle used by this instance. */
  H handle();

  /** Default transaction propagation for this engine instance (used by {@link #inTx(Supplier)}). */
  Propagation defaultPropagation();

  /** Run work within a transaction boundary using the given propagation behavior. */
  <T> T inTx(Propagation propagation, Supplier<T> work);

  /** Run work using this engine instance's {@link #defaultPropagation()}.\n */
  default <T> T inTx(Supplier<T> work) {
    return inTx(defaultPropagation(), work);
  }

  <T> List<T> select(EntityViewRef ref, Query query);

  long count(EntityViewRef ref, Query query);

  /** Insert a POJO. If the ID is auto-generated, the POJO is returned with ID populated. */
  <T> T insert(EntityViewRef ref, T entity);

  /** Bulk insert. For now returns void (future: return failed objects). */
  <T> void bulkInsert(EntityViewRef ref, List<T> entities);

  /** Upsert a POJO. If the ID is auto-generated, the POJO is returned with ID populated. */
  <T> T upsert(EntityViewRef ref, T entity);

  /** Bulk upsert. For now returns void (future: return failed objects). */
  <T> void bulkUpsert(EntityViewRef ref, List<T> entities);

  <T> long update(EntityViewRef ref, T entity);

  <T> long bulkUpdate(EntityViewRef ref, List<T> entities);

  <T> long updateByCriteria(EntityViewRef ref, Query query, T entity);

  long deleteByCriteria(EntityViewRef ref, Query query);
}



