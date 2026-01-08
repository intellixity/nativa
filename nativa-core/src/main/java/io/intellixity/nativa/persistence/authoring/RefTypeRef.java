package io.intellixity.nativa.persistence.authoring;

public record RefTypeRef(
    String refEntityAuthoringId,
    String refJavaType
) implements TypeRef {}

