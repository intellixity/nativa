package io.intellixity.nativa.persistence.authoring;

public interface UserType<T> {
  String id();
  Class<T> javaType();
  T decode(Object raw);
  Object encode(T value);
}

