package io.intellixity.nativa.persistence.codegen;

import io.intellixity.nativa.persistence.authoring.AuthoringRegistry;
import io.intellixity.nativa.persistence.authoring.ArrayTypeRef;
import io.intellixity.nativa.persistence.authoring.EntityAuthoring;
import io.intellixity.nativa.persistence.authoring.ListTypeRef;
import io.intellixity.nativa.persistence.authoring.MapTypeRef;
import io.intellixity.nativa.persistence.authoring.RefTypeRef;
import io.intellixity.nativa.persistence.authoring.ScalarTypeRef;
import io.intellixity.nativa.persistence.authoring.SetTypeRef;
import io.intellixity.nativa.persistence.authoring.TypeRef;

final class TypeJava {
  private TypeJava() {}

  static String javaType(TypeRef t, AuthoringRegistry reg) {
    if (t instanceof ScalarTypeRef s) {
      return switch (s.userTypeId()) {
        case "string" -> "String";
        case "int" -> "Integer";
        case "long" -> "Long";
        case "bool" -> "Boolean";
        case "double" -> "Double";
        case "uuid" -> "java.util.UUID";
        case "instant" -> "java.time.Instant";
        default -> "Object";
      };
    }
    if (t instanceof RefTypeRef rr) {
      EntityAuthoring ea = reg.getEntityAuthoring(rr.refEntityAuthoringId());
      return ea.javaType();
    }
    if (t instanceof ListTypeRef lt) return "java.util.List<" + javaType(lt.element(), reg) + ">";
    if (t instanceof SetTypeRef st) return "java.util.Set<" + javaType(st.element(), reg) + ">";
    if (t instanceof ArrayTypeRef at) return javaType(at.element(), reg) + "[]";
    if (t instanceof MapTypeRef mt) return "java.util.Map<" + javaType(mt.key(), reg) + "," + javaType(mt.value(), reg) + ">";
    return "Object";
  }

  static String simpleName(String fqcn) {
    int i = fqcn.lastIndexOf('.');
    return i < 0 ? fqcn : fqcn.substring(i + 1);
  }
}

