package io.intellixity.nativa.persistence.governance;

import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.exec.handle.EngineHandle;

@FunctionalInterface
public interface DataEngineFactory {
  DataEngine<? extends EngineHandle<?>> create(String engineFamily, EngineHandle<?> handle);
}




