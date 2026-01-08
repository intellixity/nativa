package io.intellixity.nativa.persistence.codegen;

import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.codegen.internal.JavaFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AccessorRegistryGenerator {
  private final List<EntityAuthoring> authorings;

  AccessorRegistryGenerator(List<EntityAuthoring> authorings) {
    this.authorings = authorings;
  }

  void generate(Path outDir) throws IOException {
    String pkg = "io.intellixity.nativa.persistence.generated";
    String type = "GeneratedPojoAccessorRegistry";

    Set<String> imports = new LinkedHashSet<>();
    imports.add("io.intellixity.nativa.persistence.pojo.PojoAccessor");
    imports.add("io.intellixity.nativa.persistence.pojo.PojoAccessorRegistry");
    imports.add("java.util.Map");
    imports.add("java.util.HashMap");

    for (EntityAuthoring ea : authorings) {
      if (!ea.generatePojo()) continue;
      imports.add(pkg(ea.javaType()) + "." + simple(ea.javaType()) + "PojoAccessor");
    }

    try (JavaFiles.IndentedWriter w = JavaFiles.open(outDir, pkg, type)) {
      w.println("package " + pkg + ";");
      w.blank();
      for (String imp : imports) w.println("import " + imp + ";");
      w.blank();

      w.println("public final class " + type + " implements PojoAccessorRegistry {");
      w.indent();

      w.println("private final Map<String, PojoAccessor<?>> byId = new HashMap<>();");
      w.blank();
      w.println("public " + type + "() {");
      w.indent();
      for (EntityAuthoring ea : authorings) {
        if (!ea.generatePojo()) continue;
        w.println("byId.put(\"" + ea.type() + "\", " + simple(ea.javaType()) + "PojoAccessor.INSTANCE);");
      }
      w.outdent();
      w.println("}");
      w.blank();

      w.println("@Override");
      w.println("public PojoAccessor<?> accessorFor(String authoringId) {");
      w.indent();
      w.println("PojoAccessor<?> a = byId.get(authoringId);");
      w.println("if (a == null) throw new IllegalArgumentException(\"No PojoAccessor generated for authoringId: \" + authoringId);");
      w.println("return a;");
      w.outdent();
      w.println("}");

      w.outdent();
      w.println("}");
    }
  }

  private static String pkg(String fqcn) {
    if (fqcn == null) return "";
    int i = fqcn.lastIndexOf('.');
    return i < 0 ? "" : fqcn.substring(0, i);
  }

  private static String simple(String fqcn) {
    if (fqcn == null) return "";
    int i = fqcn.lastIndexOf('.');
    return i < 0 ? fqcn : fqcn.substring(i + 1);
  }
}


