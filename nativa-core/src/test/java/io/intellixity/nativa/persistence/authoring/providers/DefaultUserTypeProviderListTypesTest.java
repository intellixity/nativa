package io.intellixity.nativa.persistence.authoring.providers;

import io.intellixity.nativa.persistence.authoring.UserType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class DefaultUserTypeProviderListTypesTest {

  private static UserType<?> byId(DefaultUserTypeProvider p, String id) {
    return p.userTypes().stream()
        .filter(ut -> id.equals(ut.id()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing userType: " + id));
  }

  @Test
  void listInt_encodeDecode_isStable() {
    DefaultUserTypeProvider p = new DefaultUserTypeProvider();
    @SuppressWarnings("unchecked")
    UserType<List<Integer>> ut = (UserType<List<Integer>>) byId(p, "list<int>");

    assertEquals(List.of(1, 2), ut.encode(List.of(1, 2)));
    assertEquals(List.of(1, 2), ut.decode(List.of(1, 2L))); // mixed number types
    assertEquals(List.of(1, 2), ut.decode(new Object[]{1, "2"}));
  }

  @Test
  void listLong_encodeDecode_isStable() {
    DefaultUserTypeProvider p = new DefaultUserTypeProvider();
    @SuppressWarnings("unchecked")
    UserType<List<Long>> ut = (UserType<List<Long>>) byId(p, "list<long>");

    assertEquals(List.of(1L, 2L), ut.encode(List.of(1L, 2L)));
    assertEquals(List.of(1L, 2L), ut.decode(List.of(1, 2L)));
    assertEquals(List.of(1L, 2L), ut.decode(new Object[]{"1", 2}));
  }

  @Test
  void listBool_encodeDecode_isStable() {
    DefaultUserTypeProvider p = new DefaultUserTypeProvider();
    @SuppressWarnings("unchecked")
    UserType<List<Boolean>> ut = (UserType<List<Boolean>>) byId(p, "list<bool>");

    assertEquals(List.of(true, false), ut.encode(List.of(true, false)));
    assertEquals(List.of(true, false), ut.decode(List.of(true, 0)));
    assertEquals(List.of(true, false), ut.decode(new Object[]{"true", "false"}));
  }

  @Test
  void listUuid_encodeDecode_isStable() {
    DefaultUserTypeProvider p = new DefaultUserTypeProvider();
    @SuppressWarnings("unchecked")
    UserType<List<UUID>> ut = (UserType<List<UUID>>) byId(p, "list<uuid>");

    UUID u1 = UUID.randomUUID();
    UUID u2 = UUID.randomUUID();
    assertEquals(List.of(u1, u2), ut.encode(List.of(u1, u2)));
    assertEquals(List.of(u1, u2), ut.decode(List.of(u1.toString(), u2)));
  }

  @Test
  void listDouble_encodeDecode_isStable() {
    DefaultUserTypeProvider p = new DefaultUserTypeProvider();
    @SuppressWarnings("unchecked")
    UserType<List<Double>> ut = (UserType<List<Double>>) byId(p, "list<double>");

    assertEquals(List.of(1.5d, 2.0d), ut.encode(List.of(1.5d, 2.0d)));
    assertEquals(List.of(1.5d, 2.0d), ut.decode(List.of(1.5d, 2)));
    assertEquals(List.of(1.5d, 2.0d), ut.decode(new Object[]{"1.5", 2}));
  }
}


