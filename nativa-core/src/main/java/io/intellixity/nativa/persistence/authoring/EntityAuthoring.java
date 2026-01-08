package io.intellixity.nativa.persistence.authoring;

public record EntityAuthoring(
    String type,
    AuthoringKind kind,
    /** Required for kind=ENTITY; table/collection name. Not applicable for VALUE. */
    String source,
    /** Fully-qualified Java type name (FQCN), e.g. com.acme.domain.Address */
    String javaType,
    /** Whether codegen should generate POJO/RowReader/PojoAccessor. Defaults to true in YAML loader. */
    boolean generatePojo,
    java.util.Map<String, FieldDef> fields,
    java.util.Map<String, ViewDef> views
) {
  public EntityAuthoring {
    kind = kind == null ? AuthoringKind.ENTITY : kind;
    if (javaType == null || javaType.isBlank()) throw new IllegalArgumentException("javaType is required for authoring: " + type);
    if (!javaType.contains(".")) throw new IllegalArgumentException("javaType must be FQCN (e.g. com.acme.Foo) for authoring: " + type);

    if (kind == AuthoringKind.ENTITY) {
      if (source == null || source.isBlank()) throw new IllegalArgumentException("source is required for ENTITY authoring: " + type);
    } else {
      if (source != null && !source.isBlank()) {
        throw new IllegalArgumentException("source is not applicable for VALUE authoring: " + type);
      }
      source = null;
    }

    fields = fields == null ? java.util.Map.of() : java.util.Map.copyOf(fields);
    views = views == null ? java.util.Map.of() : java.util.Map.copyOf(views);
  }
}

