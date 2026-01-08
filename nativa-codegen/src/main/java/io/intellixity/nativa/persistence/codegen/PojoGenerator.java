package io.intellixity.nativa.persistence.codegen;

import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.codegen.internal.JavaFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

final class PojoGenerator {
  private final AuthoringRegistry reg;

  PojoGenerator(AuthoringRegistry reg) {
    this.reg = reg;
  }

  void generatePojoIfNeeded(EntityAuthoring ea, Path outDir) throws IOException {
    if (!ea.generatePojo()) return;

    String pkg = pkg(ea.javaType());
    String type = simple(ea.javaType());

    // Collect field information
    Set<String> imports = new LinkedHashSet<>();
    List<FieldInfo> fields = new ArrayList<>();

    imports.add("io.intellixity.nativa.persistence.pojo.Nulls");
    imports.add("java.util.Set");
    imports.add("java.util.HashSet");

    for (var e : ea.fields().entrySet()) {
      String name = e.getKey();
      TypeRef tr = e.getValue().type();
      String jt = TypeJava.javaType(tr, reg);
      collectImports(imports, tr);
      fields.add(new FieldInfo(name, jt, TypeJava.simpleName(jt)));
    }

    try (JavaFiles.IndentedWriter w = JavaFiles.open(outDir, pkg, type)) {
      if (pkg != null && !pkg.isBlank()) {
        w.println("package " + pkg + ";");
        w.blank();
      }

      for (String imp : imports) {
        w.println("import " + imp + ";");
      }
      if (!imports.isEmpty()) w.blank();

      // Generate POJO class
      w.println("public final class " + type + " implements Nulls {");
      w.indent();

      // Field name constants (top-level)
      for (var e : ea.fields().entrySet()) {
        String field = e.getKey();
        TypeRef tr = e.getValue().type();
        // For ref fields, we expose a nested Props holder instead of a String constant to avoid name collisions.
        if (tr instanceof RefTypeRef) continue;
        w.println("public static final String " + constName(field) + " = \"" + field + "\";");
      }
      w.blank();

      // Nested property constants for refs (Order.CUSTOMER.FIRST_NAME)
      for (var e : ea.fields().entrySet()) {
        TypeRef tr = e.getValue().type();
        if (tr instanceof RefTypeRef rr) {
          EntityAuthoring child = reg.getEntityAuthoring(rr.refEntityAuthoringId());
          String propsType = simple(child.javaType()) + "Props";
          String fieldName = e.getKey();
          // Also expose the ref field path as a String constant for codegen (e.g., Order.CUSTOMER_PATH == "customer")
          // to avoid hardcoded "customer" in generated PojoAccessor.
          w.println("public static final String " + constName(fieldName) + "_PATH = \"" + fieldName + "\";");
          w.println("public static final " + propsType + " " + constName(fieldName) + " = new " + propsType + "(\"" + fieldName + "\");");
        }
      }
      if (ea.fields().values().stream().anyMatch(fd -> fd.type() instanceof RefTypeRef)) w.blank();

      for (var e : ea.fields().entrySet()) {
        TypeRef tr = e.getValue().type();
        if (!(tr instanceof RefTypeRef rr)) continue;
        EntityAuthoring child = reg.getEntityAuthoring(rr.refEntityAuthoringId());
        String childType = simple(child.javaType());
        String propsType = childType + "Props";
        w.println("public static final class " + propsType + " {");
        w.indent();
        w.println("private final String prefix;");
        for (var cf : child.fields().keySet()) {
          w.println("public final String " + constName(cf) + ";");
        }
        w.blank();
        w.println("private " + propsType + "(String prefix) {");
        w.indent();
        w.println("this.prefix = prefix;");
        for (var cf : child.fields().keySet()) {
          w.println("this." + constName(cf) + " = prefix + \".\" + \"" + cf + "\";");
        }
        w.outdent();
        w.println("}");
        w.outdent();
        w.println("}");
        w.blank();
      }

      // Generate fields
      for (FieldInfo field : fields) {
        w.println("private " + field.simpleType + " " + field.name + ";");
      }
      w.println("private final Set<String> _nulls = new HashSet<>();");
      w.blank();

      // Public no-arg constructor (mutable POJO)
      w.println("public " + type + "() {}");
      w.blank();

      w.println("@Override");
      w.println("public Set<String> getNulls() { return _nulls; }");
      w.blank();

      // Generate fluent accessors (existing behavior)
      for (FieldInfo field : fields) {
        w.println("public " + field.simpleType + " " + field.name + "() {");
        w.indent();
        w.println("return " + field.name + ";");
        w.outdent();
        w.println("}");
        w.blank();
      }

      // Generate JavaBean getters (getX / isX) for framework compatibility (Jackson, etc.)
      for (FieldInfo field : fields) {
        String suffix = cap(field.name);
        // isX for booleans (we treat both boolean and Boolean as boolean-like here)
        if (isBooleanLike(field.fullType, field.simpleType)) {
          w.println("public " + field.simpleType + " is" + suffix + "() {");
          w.indent();
          w.println("return " + field.name + ";");
          w.outdent();
          w.println("}");
          w.blank();
        }
        // getX for everything
        w.println("public " + field.simpleType + " get" + suffix + "() {");
        w.indent();
        w.println("return " + field.name + ";");
        w.outdent();
        w.println("}");
        w.blank();
      }

      // Generate fluent setters (same name as field; overload with 1 param)
      for (FieldInfo field : fields) {
        w.println("public " + type + " " + field.name + "(" + field.simpleType + " " + field.name + ") {");
        w.indent();
        w.println("this." + field.name + " = " + field.name + ";");
        w.println("return this;");
        w.outdent();
        w.println("}");
        w.blank();
      }

      // Generate builder() static method
      w.println("public static Builder builder() {");
      w.indent();
      w.println("return new Builder();");
      w.outdent();
      w.println("}");
      w.blank();

      // Generate Builder class
      w.println("public static final class Builder {");
      w.indent();

      // Builder fields
      for (FieldInfo field : fields) {
        w.println("private " + field.simpleType + " " + field.name + ";");
      }
      w.blank();

      // Builder setters
      for (FieldInfo field : fields) {
        w.println("public Builder " + field.name + "(" + field.simpleType + " " + field.name + ") {");
        w.indent();
        w.println("this." + field.name + " = " + field.name + ";");
        w.println("return this;");
        w.outdent();
        w.println("}");
        w.blank();
      }

      // Builder build() method
      w.println("public " + type + " build() {");
      w.indent();
      w.println(type + " out = new " + type + "();");
      for (FieldInfo field : fields) {
        w.println("out." + field.name + "(this." + field.name + ");");
      }
      w.println("return out;");
      w.outdent();
      w.println("}");

      w.outdent();
      w.println("}"); // End Builder class

      w.outdent();
      w.println("}"); // End POJO class
    }
  }

  private record FieldInfo(String name, String fullType, String simpleType) {}

  private static boolean isBooleanLike(String fullType, String simpleType) {
    if (simpleType == null) return false;
    if ("boolean".equals(simpleType) || "Boolean".equals(simpleType)) return true;
    if (fullType == null) return false;
    return "boolean".equals(fullType) || "Boolean".equals(fullType) || fullType.endsWith(".Boolean");
  }

  private static String cap(String name) {
    if (name == null || name.isBlank()) return "";
    if (name.length() == 1) return name.toUpperCase(Locale.ROOT);
    char c0 = name.charAt(0);
    return Character.toUpperCase(c0) + name.substring(1);
  }

  private static String constName(String fieldName) {
    if (fieldName == null || fieldName.isBlank()) return "FIELD";
    StringBuilder out = new StringBuilder(fieldName.length() + 8);
    char prev = 0;
    for (int i = 0; i < fieldName.length(); i++) {
      char c = fieldName.charAt(i);
      if (c == '.' || c == '-' || c == ' ') c = '_';
      boolean upperBoundary = (i > 0) && Character.isUpperCase(c) && Character.isLowerCase(prev);
      if (upperBoundary) out.append('_');
      out.append(Character.toUpperCase(c));
      prev = c;
    }
    // collapse double underscores
    String s = out.toString().replaceAll("__+", "_");
    if (s.startsWith("_")) s = s.substring(1);
    if (s.endsWith("_")) s = s.substring(0, s.length() - 1);
    return s;
  }

  private void collectImports(Set<String> imports, TypeRef t) {
    if (t instanceof ScalarTypeRef s) {
      String jt = TypeJava.javaType(s, reg);
      if (jt.contains(".")) imports.add(jt);
      return;
    }
    if (t instanceof RefTypeRef rr) {
      EntityAuthoring ea = reg.getEntityAuthoring(rr.refEntityAuthoringId());
      imports.add(ea.javaType());
      return;
    }
    if (t instanceof ListTypeRef lt) {
      imports.add("java.util.List");
      collectImports(imports, lt.element());
      return;
    }
    if (t instanceof SetTypeRef st) {
      imports.add("java.util.Set");
      collectImports(imports, st.element());
      return;
    }
    if (t instanceof ArrayTypeRef at) {
      collectImports(imports, at.element());
      return;
    }
    if (t instanceof MapTypeRef mt) {
      imports.add("java.util.Map");
      collectImports(imports, mt.key());
      collectImports(imports, mt.value());
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

