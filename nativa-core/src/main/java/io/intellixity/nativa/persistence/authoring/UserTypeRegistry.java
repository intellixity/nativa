package io.intellixity.nativa.persistence.authoring;

public interface UserTypeRegistry {
  UserType<?> get(String userTypeId);
}

