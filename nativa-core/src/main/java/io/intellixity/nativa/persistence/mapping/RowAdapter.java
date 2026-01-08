package io.intellixity.nativa.persistence.mapping;

import io.intellixity.nativa.persistence.authoring.UserType;
import io.intellixity.nativa.persistence.authoring.UserTypeRegistry;

import java.util.Map;

public interface RowAdapter {
  UserTypeRegistry userTypes();

  boolean isNull(String path);
  Object raw(String path);

  default <T> T decode(String path, String userTypeId) {
    @SuppressWarnings("unchecked")
    UserType<T> t = (UserType<T>) userTypes().get(userTypeId);
    return t.decode(raw(path));
  }

  default <T> T decodeRaw(Object raw, String userTypeId) {
    @SuppressWarnings("unchecked")
    UserType<T> t = (UserType<T>) userTypes().get(userTypeId);
    return t.decode(raw);
  }

  RowAdapter object(String pathOrPrefix);
  Iterable<Object> arrayRaw(String path);
  Map<String, Object> map(String path);
}

