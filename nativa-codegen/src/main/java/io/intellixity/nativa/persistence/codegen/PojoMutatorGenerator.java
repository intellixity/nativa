package io.intellixity.nativa.persistence.codegen;

import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.codegen.internal.JavaFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

final class PojoMutatorGenerator {
  private final AuthoringRegistry reg;

  PojoMutatorGenerator(AuthoringRegistry reg) {
    this.reg = reg;
  }

  void generateMutatorIfNeeded(EntityAuthoring ea, Path outDir) throws IOException {
    if (!ea.generatePojo()) return;

    String pkg = pkg(ea.javaType());
    String type = simple(ea.javaType());
    String mutatorName = type + "PojoMutator";

    Set<String> imports = new LinkedHashSet<>();
    imports.add("io.intellixity.nativa.persistence.pojo.PojoMutator");
    imports.add(ea.javaType());

    // imports for nested refs/value types used directly in mutator (method calls)
    for (var f : ea.fields().entrySet()) {
      TypeRef t = f.getValue().type();
      if (t instanceof RefTypeRef rr) {
        EntityAuthoring child = reg.getEntityAuthoring(rr.refEntityAuthoringId());
        imports.add(child.javaType());
      }
    }

    try (JavaFiles.IndentedWriter w = JavaFiles.open(outDir, pkg, mutatorName)) {
      if (pkg != null && !pkg.isBlank()) {
        w.println("package " + pkg + ";");
        w.blank();
      }
      for (String imp : imports) w.println("import " + imp + ";");
      w.blank();

      w.println("public final class " + mutatorName + " implements PojoMutator<" + type + "> {");
      w.indent();
      w.println("public static final " + mutatorName + " INSTANCE = new " + mutatorName + "();");
      w.println("private " + mutatorName + "() {}");
      w.blank();

      w.println("@Override");
      w.println("public void set(" + type + " pojo, String field, Object value) {");
      w.indent();
      w.println("if (pojo == null) return;");
      w.println("if (field == null) return;");
      w.println("switch (field) {");
      w.indent();

      for (var f : ea.fields().entrySet()) {
        String field = f.getKey();
        FieldDef fd = f.getValue();
        TypeRef tr = fd.type();

        // use generated constants so callers can avoid hardcoded strings
        String constExpr;
        if (tr instanceof RefTypeRef) constExpr = type + "." + constName(field) + "_PATH";
        else constExpr = type + "." + constName(field);

        String castType = rawJavaType(TypeJava.javaType(tr, reg));
        w.println("case " + constExpr + " -> pojo." + field + "((" + castType + ") value);");
      }

      w.println("default -> { }");
      w.outdent();
      w.println("}");
      w.outdent();
      w.println("}");

      w.outdent();
      w.println("}");
    }
  }

  private static String rawJavaType(String jt) {
    if (jt == null || jt.isBlank()) return "Object";
    int lt = jt.indexOf('<');
    if (lt > 0) return jt.substring(0, lt);
    return jt;
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

  private static String constName(String field) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < field.length(); i++) {
      char c = field.charAt(i);
      if (Character.isUpperCase(c) && i > 0) sb.append('_');
      sb.append(Character.toUpperCase(c));
    }
    return sb.toString();
  }
}


