package io.intellixity.nativa.persistence.mapping;

public interface RowReader<T> {
  T read(RowAdapter row);
}

