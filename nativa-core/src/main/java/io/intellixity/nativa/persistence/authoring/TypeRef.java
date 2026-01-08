package io.intellixity.nativa.persistence.authoring;

public sealed interface TypeRef permits ScalarTypeRef, RefTypeRef, ListTypeRef, SetTypeRef, ArrayTypeRef, MapTypeRef {}

