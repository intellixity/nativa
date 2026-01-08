package io.intellixity.nativa.persistence.authoring;

/** Canonical user type IDs for complex {@link TypeRef} shapes. */
public final class TypeIds {
  private TypeIds() {}

  /** Returns canonical ID for the given type (used for UserTypeRegistry lookup). */
  public static String id(TypeRef t) {
    if (t == null) return "json";
    if (t instanceof ScalarTypeRef s) return s.userTypeId();
    if (t instanceof RefTypeRef) return "ref";
    if (t instanceof ListTypeRef lt) return "list<" + id(lt.element()) + ">";
    if (t instanceof SetTypeRef st) return "set<" + id(st.element()) + ">";
    if (t instanceof ArrayTypeRef at) return "array<" + id(at.element()) + ">";
    if (t instanceof MapTypeRef mt) return "map<" + id(mt.key()) + "," + id(mt.value()) + ">";
    return "json";
  }
}





