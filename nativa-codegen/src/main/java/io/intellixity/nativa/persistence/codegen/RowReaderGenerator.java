package io.intellixity.nativa.persistence.codegen;

import io.intellixity.nativa.persistence.authoring.*;
import io.intellixity.nativa.persistence.codegen.internal.JavaFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

final class RowReaderGenerator {
  private final AuthoringRegistry reg;

  RowReaderGenerator(AuthoringRegistry reg) {
    this.reg = reg;
  }

  void generateRowReader(EntityAuthoring ea, Path outDir) throws IOException {
    String pkg = pkg(ea.javaType());
    String pojoFqcn = ea.javaType();
    String pojo = simple(ea.javaType());

    String rrName = ea.javaType() + "RowReader";
    rrName = pojo + "RowReader";

    Set<String> imports = new LinkedHashSet<>();
    imports.add("io.intellixity.nativa.persistence.mapping.RowAdapter");
    imports.add("io.intellixity.nativa.persistence.mapping.RowReader");
    imports.add("io.intellixity.nativa.persistence.mapping.Coercions");
    imports.add("io.intellixity.nativa.persistence.mapping.RowAdapters");

    // POJO import
    imports.add(pojoFqcn);

    // referenced entity readers
    for (var f : ea.fields().entrySet()) {
      collectImports(imports, f.getValue().type());
    }

    try (JavaFiles.IndentedWriter w = JavaFiles.open(outDir, pkg, rrName)) {
      if (pkg != null && !pkg.isBlank()) {
        w.println("package " + pkg + ";");
        w.blank();
      }

      for (String imp : imports) w.println("import " + imp + ";");
      w.blank();

      w.println("public final class " + rrName + " implements RowReader<" + pojo + "> {");
      w.indent();
      w.println("public static final " + rrName + " INSTANCE = new " + rrName + "();");
      w.blank();

      w.println("private " + rrName + "() {}");
      w.blank();

      w.println("@Override");
      w.println("public " + pojo + " read(RowAdapter row) {");
      w.indent();

      // Build using builder pattern
      w.println(pojo + ".Builder builder = " + pojo + ".builder();");
      for (var e : ea.fields().entrySet()) {
        String field = e.getKey();
        TypeRef tr = e.getValue().type();
        String readExpr = readExpr("row", field, tr);
        w.println("builder." + field + "(" + readExpr + ");");
      }
      w.println("return builder.build();");

      w.outdent();
      w.println("}");

      w.outdent();
      w.println("}");
    }
  }

  private void collectImports(Set<String> imports, TypeRef t) {
    if (t instanceof ScalarTypeRef s) {
      String jt = TypeJava.javaType(s, reg);
      if (jt.contains(".")) imports.add(jt);
      return;
    }
    if (t instanceof RefTypeRef rr) {
      EntityAuthoring child = reg.getEntityAuthoring(rr.refEntityAuthoringId());
      imports.add(child.javaType());
      imports.add(pkg(child.javaType()) + "." + simple(child.javaType()) + "RowReader");
      return;
    }
    if (t instanceof ListTypeRef lt) { imports.add("java.util.List"); collectImports(imports, lt.element()); return; }
    if (t instanceof SetTypeRef st) { imports.add("java.util.Set"); collectImports(imports, st.element()); return; }
    if (t instanceof ArrayTypeRef at) { collectImports(imports, at.element()); return; }
    if (t instanceof MapTypeRef mt) { imports.add("java.util.Map"); collectImports(imports, mt.key()); collectImports(imports, mt.value()); }
  }

  private String readExpr(String rowVar, String field, TypeRef t) {
    if (t instanceof ScalarTypeRef s) {
      return rowVar + ".decode(\"" + field + "\", \"" + s.userTypeId() + "\")";
    }

    if (t instanceof RefTypeRef rr) {
      EntityAuthoring child = reg.getEntityAuthoring(rr.refEntityAuthoringId());
      String childPojo = simple(child.javaType());
      String childRR = childPojo + "RowReader";
      // dot-path convention: field.subfield in SELECT; so prefix adapter uses "field."
      return childRR + ".INSTANCE.read(" + rowVar + ".object(\"" + field + ".\"))";
    }

    if (t instanceof ListTypeRef lt) {
      if (lt.element() instanceof ScalarTypeRef) {
        return rowVar + ".decode(\"" + field + "\", \"" + typeId(t) + "\")";
      }
      return listExpr(rowVar, field, lt.element());
    }

    if (t instanceof SetTypeRef st) {
      if (st.element() instanceof ScalarTypeRef) {
        return rowVar + ".decode(\"" + field + "\", \"" + typeId(t) + "\")";
      }
      return setExpr(rowVar, field, st.element());
    }

    if (t instanceof ArrayTypeRef at) {
      if (at.element() instanceof ScalarTypeRef) {
        return rowVar + ".decode(\"" + field + "\", \"" + typeId(t) + "\")";
      }
      return arrayExpr(rowVar, field, at.element());
    }

    if (t instanceof MapTypeRef mt) {
      if (mt.key() instanceof ScalarTypeRef && mt.value() instanceof ScalarTypeRef) {
        return rowVar + ".decode(\"" + field + "\", \"" + typeId(t) + "\")";
      }
      // v0 fallback: map is decoded from raw map value
      String dk = decodeRawExpr(rowVar, "k", mt.key());
      String dv = decodeRawExpr(rowVar, "v", mt.value());
      return "Coercions.toMap(" + rowVar + ".raw(\"" + field + "\"), k -> " + dk + ", v -> " + dv + ")";
    }

    return rowVar + ".raw(\"" + field + "\")";
  }

  private static String typeId(TypeRef t) {
    if (t == null) return "json";
    if (t instanceof ScalarTypeRef s) return s.userTypeId();
    if (t instanceof RefTypeRef) return "ref";
    if (t instanceof ListTypeRef lt) return "list<" + typeId(lt.element()) + ">";
    if (t instanceof SetTypeRef st) return "set<" + typeId(st.element()) + ">";
    if (t instanceof ArrayTypeRef at) return "array<" + typeId(at.element()) + ">";
    if (t instanceof MapTypeRef mt) return "map<" + typeId(mt.key()) + "," + typeId(mt.value()) + ">";
    return "json";
  }

  private String listExpr(String rowVar, String field, TypeRef el) {
    if (el instanceof RefTypeRef rr) {
      EntityAuthoring child = reg.getEntityAuthoring(rr.refEntityAuthoringId());
      String childRR = simple(child.javaType()) + "RowReader";
      return "Coercions.toList(" + rowVar + ".arrayRaw(\"" + field + "\"), raw -> " + childRR + ".INSTANCE.read(RowAdapters.fromObject(raw, " + rowVar + ".userTypes())))";
    }
    return "Coercions.toList(" + rowVar + ".arrayRaw(\"" + field + "\"), raw -> " + decodeRawExpr(rowVar, "raw", el) + ")";
  }

  private String setExpr(String rowVar, String field, TypeRef el) {
    if (el instanceof RefTypeRef rr) {
      EntityAuthoring child = reg.getEntityAuthoring(rr.refEntityAuthoringId());
      String childRR = simple(child.javaType()) + "RowReader";
      return "Coercions.toSet(" + rowVar + ".arrayRaw(\"" + field + "\"), raw -> " + childRR + ".INSTANCE.read(RowAdapters.fromObject(raw, " + rowVar + ".userTypes())))";
    }
    return "Coercions.toSet(" + rowVar + ".arrayRaw(\"" + field + "\"), raw -> " + decodeRawExpr(rowVar, "raw", el) + ")";
  }

  private String arrayExpr(String rowVar, String field, TypeRef el) {
    String jt = TypeJava.javaType(el, reg);
    String simple = TypeJava.simpleName(jt);
    if (el instanceof RefTypeRef rr) {
      EntityAuthoring child = reg.getEntityAuthoring(rr.refEntityAuthoringId());
      String childRR = simple(child.javaType()) + "RowReader";
      return "Coercions.toArray(" + rowVar + ".arrayRaw(\"" + field + "\"), " + simple + "[]::new, raw -> " + childRR + ".INSTANCE.read(RowAdapters.fromObject(raw, " + rowVar + ".userTypes())))";
    }
    return "Coercions.toArray(" + rowVar + ".arrayRaw(\"" + field + "\"), " + simple + "[]::new, raw -> " + decodeRawExpr(rowVar, "raw", el) + ")";
  }

  private String decodeRawExpr(String rowVar, String rawVar, TypeRef t) {
    if (t instanceof ScalarTypeRef s) {
      return rowVar + ".decodeRaw(" + rawVar + ", \"" + s.userTypeId() + "\")";
    }
    return rawVar;
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

