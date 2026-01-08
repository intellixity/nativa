package io.intellixity.nativa.persistence.codegen;

import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.codegen.internal.JavaFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class MutatorRegistryGenerator {
  private final List<EntityAuthoring> authorings;

  MutatorRegistryGenerator(List<EntityAuthoring> authorings) {
    this.authorings = authorings;
  }

  void generate(Path outDir) throws IOException {
    String pkg = "io.intellixity.nativa.persistence.generated";
    String type = "GeneratedPojoMutatorRegistry";

    Set<String> imports = new LinkedHashSet<>();
    imports.add("io.intellixity.nativa.persistence.pojo.PojoMutator");
    imports.add("io.intellixity.nativa.persistence.pojo.PojoMutatorRegistry");
    imports.add("java.util.Map");
    imports.add("java.util.HashMap");

    for (EntityAuthoring ea : authorings) {
      if (!ea.generatePojo()) continue;
      imports.add(pkg(ea.javaType()) + "." + simple(ea.javaType()) + "PojoMutator");
    }

    try (JavaFiles.IndentedWriter w = JavaFiles.open(outDir, pkg, type)) {
      w.println("package " + pkg + ";");
      w.blank();
      for (String imp : imports) w.println("import " + imp + ";");
      w.blank();

      w.println("public final class " + type + " implements PojoMutatorRegistry {");
      w.indent();

      w.println("private final Map<String, PojoMutator<?>> byId = new HashMap<>();");
      w.blank();
      w.println("public " + type + "() {");
      w.indent();
      for (EntityAuthoring ea : authorings) {
        if (!ea.generatePojo()) continue;
        w.println("byId.put(\"" + ea.type() + "\", " + simple(ea.javaType()) + "PojoMutator.INSTANCE);");
      }
      w.outdent();
      w.println("}");
      w.blank();

      w.println("@Override");
      w.println("public PojoMutator<?> mutatorFor(String authoringId) {");
      w.indent();
      w.println("PojoMutator<?> m = byId.get(authoringId);");
      w.println("if (m == null) throw new IllegalArgumentException(\"No PojoMutator generated for authoringId: \" + authoringId);");
      w.println("return m;");
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




