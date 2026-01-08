package io.intellixity.nativa.persistence.pojo;

public interface PojoMutatorRegistry {
  PojoMutator<?> mutatorFor(String authoringId);
}




