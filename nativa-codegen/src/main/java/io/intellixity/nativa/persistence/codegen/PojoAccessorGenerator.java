package io.intellixity.nativa.persistence.codegen;

import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.codegen.internal.JavaFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

final class PojoAccessorGenerator {
  private final AuthoringRegistry reg;

  PojoAccessorGenerator(AuthoringRegistry reg) {
    this.reg = reg;
  }

  void generateAccessorIfNeeded(EntityAuthoring ea, Path outDir) throws IOException {
    if (!ea.generatePojo()) return;

    String pkg = pkg(ea.javaType());
    String type = simple(ea.javaType());
    String accessorName = type + "PojoAccessor";

    Set<String> imports = new LinkedHashSet<>();
    imports.add("io.intellixity.nativa.persistence.pojo.PojoAccessor");
    imports.add(ea.javaType());

    // imports for nested refs/value types used directly in accessor (method calls)
    for (var f : ea.fields().entrySet()) {
      TypeRef t = f.getValue().type();
      if (t instanceof RefTypeRef rr) {
        EntityAuthoring child = reg.getEntityAuthoring(rr.refEntityAuthoringId());
        imports.add(child.javaType());
      }
    }

    try (JavaFiles.IndentedWriter w = JavaFiles.open(outDir, pkg, accessorName)) {
      if (pkg != null && !pkg.isBlank()) {
        w.println("package " + pkg + ";");
        w.blank();
      }
      for (String imp : imports) w.println("import " + imp + ";");
      w.blank();

      w.println("public final class " + accessorName + " implements PojoAccessor<" + type + "> {");
      w.indent();
      w.println("public static final " + accessorName + " INSTANCE = new " + accessorName + "();");
      w.println("private " + accessorName + "() {}");
      w.blank();

      w.println("@Override");
      w.println("public Object get(" + type + " pojo, String path) {");
      w.indent();
      w.println("if (pojo == null) return null;");
      w.println("if (path == null) return null;");
      w.blank();

      for (var f : ea.fields().entrySet()) {
        String field = f.getKey();
        TypeRef tr = f.getValue().type();
        if (tr instanceof RefTypeRef rr) {
          EntityAuthoring child = reg.getEntityAuthoring(rr.refEntityAuthoringId());
          String childType = simple(child.javaType());
          // direct field (use generated constant: Order.CUSTOMER_PATH)
          w.println("if (" + type + "." + constName(field) + "_PATH.equals(path)) return pojo." + field + "();");
          // nested scalar fields
          for (var cf : child.fields().entrySet()) {
            String childField = cf.getKey();
            // Order.CUSTOMER.FIRST_NAME, etc. (not a compile-time constant, so use equals-check, not switch)
            w.println("if (" + type + "." + constName(field) + "." + constName(childField) + ".equals(path)) {");
            w.indent();
            w.println(childType + " c = pojo." + field + "();");
            w.println("return (c == null) ? null : c." + childField + "();");
            w.outdent();
            w.println("}");
          }
        } else {
          // scalar/container field (use compile-time constant: Order.STATUS, Order.TENANT_ID, etc.)
          w.println("if (" + type + "." + constName(field) + ".equals(path)) return pojo." + field + "();");
        }
      }

      w.println("return null;");
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


