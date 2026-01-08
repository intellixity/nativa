package io.intellixity.nativa.persistence.authoring.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.intellixity.nativa.persistence.authoring.DiscoveredUserTypeRegistry;
import io.intellixity.nativa.persistence.authoring.UserType;
import io.intellixity.nativa.persistence.authoring.UserTypeProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Global built-in scalar user types (dialectId="*"). */
public final class DefaultUserTypeProvider implements UserTypeProvider {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public String dialectId() {
    return DiscoveredUserTypeRegistry.GLOBAL_DIALECT;
  }

  @Override
  public Collection<UserType<?>> userTypes() {
    return List.of(
        DiscoveredUserTypeRegistry.GlobalTypes.string(),
        DiscoveredUserTypeRegistry.GlobalTypes.integer(),
        DiscoveredUserTypeRegistry.GlobalTypes.longType(),
        DiscoveredUserTypeRegistry.GlobalTypes.bool(),
        DiscoveredUserTypeRegistry.GlobalTypes.doubleType(),
        DiscoveredUserTypeRegistry.GlobalTypes.uuid(),
        DiscoveredUserTypeRegistry.GlobalTypes.instant(),
        new JsonStringUserType(),
        new ListStringUserType(),
        new ListIntUserType(),
        new ListLongUserType(),
        new ListBoolUserType(),
        new ListUuidUserType(),
        new ListDoubleUserType()
    );
  }

  /** Global JSON user type: encode objects as JSON strings; decode JSON strings back to Objects. */
  static final class JsonStringUserType implements UserType<Object> {
    @Override public String id() { return "json"; }
    @Override public Class<Object> javaType() { return Object.class; }

    @Override
    public Object decode(Object raw) {
      if (raw == null) return null;
      if (raw instanceof String s) {
        try {
          return JSON.readValue(s, Object.class);
        } catch (Exception e) {
          // If it's not valid JSON, just return the string.
          return s;
        }
      }
      return raw;
    }

    @Override
    public Object encode(Object value) {
      if (value == null) return null;
      if (value instanceof String) return value;
      try {
        return JSON.writeValueAsString(value);
      } catch (Exception e) {
        throw new IllegalArgumentException("Failed to JSON-encode value", e);
      }
    }
  }

  /**
   * Global logical list<string> type.\n
   *
   * - Encode: List<String> (stable logical encoding)\n
   * - Decode: supports JDBC Array/Object[]/List and JSON string fallback.\n
   */
  static final class ListStringUserType implements UserType<List<String>> {
    @Override public String id() { return "list<string>"; }
    @SuppressWarnings("unchecked")
    @Override public Class<List<String>> javaType() { return (Class<List<String>>) (Class<?>) List.class; }

    @Override
    public List<String> decode(Object raw) {
      if (raw == null) return null;
      if (raw instanceof Object[] oa) return toStringList(Arrays.asList(oa));
      if (raw instanceof List<?> l) return toStringList(l);
      if (raw instanceof String s) {
        try {
          return JSON.readValue(s, JSON.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
          return List.of(s);
        }
      }
      return List.of(String.valueOf(raw));
    }

    @Override
    public Object encode(List<String> value) {
      return value;
    }

    private static List<String> toStringList(Iterable<?> it) {
      List<String> out = new ArrayList<>();
      for (Object x : it) out.add(x == null ? null : String.valueOf(x));
      return out;
    }
  }

  /** Global logical list<int> type (stable logical encoding: List<Integer>). */
  static final class ListIntUserType implements UserType<List<Integer>> {
    @Override public String id() { return "list<int>"; }
    @SuppressWarnings("unchecked")
    @Override public Class<List<Integer>> javaType() { return (Class<List<Integer>>) (Class<?>) List.class; }

    @Override
    public List<Integer> decode(Object raw) {
      if (raw == null) return null;
      if (raw instanceof Object[] oa) return toIntList(Arrays.asList(oa));
      if (raw instanceof List<?> l) return toIntList(l);
      if (raw instanceof String s) {
        try {
          List<?> l = JSON.readValue(s, JSON.getTypeFactory().constructCollectionType(List.class, Object.class));
          return toIntList(l);
        } catch (Exception e) {
          return List.of(toInt(raw));
        }
      }
      return List.of(toInt(raw));
    }

    @Override
    public Object encode(List<Integer> value) {
      if (value == null) return null;
      List<Integer> out = new ArrayList<>(value.size());
      for (Object x : value) out.add(toInt(x));
      return out;
    }

    private static List<Integer> toIntList(Iterable<?> it) {
      List<Integer> out = new ArrayList<>();
      for (Object x : it) out.add(toInt(x));
      return out;
    }

    private static Integer toInt(Object x) {
      if (x == null) return null;
      if (x instanceof Integer i) return i;
      if (x instanceof Number n) return n.intValue();
      if (x instanceof String s) return s.isBlank() ? null : Integer.valueOf(s.trim());
      return Integer.valueOf(String.valueOf(x));
    }
  }

  /** Global logical list<long> type (stable logical encoding: List<Long>). */
  static final class ListLongUserType implements UserType<List<Long>> {
    @Override public String id() { return "list<long>"; }
    @SuppressWarnings("unchecked")
    @Override public Class<List<Long>> javaType() { return (Class<List<Long>>) (Class<?>) List.class; }

    @Override
    public List<Long> decode(Object raw) {
      if (raw == null) return null;
      if (raw instanceof Object[] oa) return toLongList(Arrays.asList(oa));
      if (raw instanceof List<?> l) return toLongList(l);
      if (raw instanceof String s) {
        try {
          List<?> l = JSON.readValue(s, JSON.getTypeFactory().constructCollectionType(List.class, Object.class));
          return toLongList(l);
        } catch (Exception e) {
          return List.of(toLong(raw));
        }
      }
      return List.of(toLong(raw));
    }

    @Override
    public Object encode(List<Long> value) {
      if (value == null) return null;
      List<Long> out = new ArrayList<>(value.size());
      for (Object x : value) out.add(toLong(x));
      return out;
    }

    private static List<Long> toLongList(Iterable<?> it) {
      List<Long> out = new ArrayList<>();
      for (Object x : it) out.add(toLong(x));
      return out;
    }

    private static Long toLong(Object x) {
      if (x == null) return null;
      if (x instanceof Long l) return l;
      if (x instanceof Number n) return n.longValue();
      if (x instanceof String s) return s.isBlank() ? null : Long.valueOf(s.trim());
      return Long.valueOf(String.valueOf(x));
    }
  }

  /** Global logical list<bool> type (stable logical encoding: List<Boolean>). */
  static final class ListBoolUserType implements UserType<List<Boolean>> {
    @Override public String id() { return "list<bool>"; }
    @SuppressWarnings("unchecked")
    @Override public Class<List<Boolean>> javaType() { return (Class<List<Boolean>>) (Class<?>) List.class; }

    @Override
    public List<Boolean> decode(Object raw) {
      if (raw == null) return null;
      if (raw instanceof Object[] oa) return toBoolList(Arrays.asList(oa));
      if (raw instanceof List<?> l) return toBoolList(l);
      if (raw instanceof String s) {
        try {
          List<?> l = JSON.readValue(s, JSON.getTypeFactory().constructCollectionType(List.class, Object.class));
          return toBoolList(l);
        } catch (Exception e) {
          return List.of(toBool(raw));
        }
      }
      return List.of(toBool(raw));
    }

    @Override
    public Object encode(List<Boolean> value) {
      if (value == null) return null;
      List<Boolean> out = new ArrayList<>(value.size());
      for (Object x : value) out.add(toBool(x));
      return out;
    }

    private static List<Boolean> toBoolList(Iterable<?> it) {
      List<Boolean> out = new ArrayList<>();
      for (Object x : it) out.add(toBool(x));
      return out;
    }

    private static Boolean toBool(Object x) {
      if (x == null) return null;
      if (x instanceof Boolean b) return b;
      if (x instanceof Number n) return n.intValue() != 0;
      String s = String.valueOf(x).trim();
      if (s.isEmpty()) return null;
      return Boolean.parseBoolean(s);
    }
  }

  /** Global logical list<uuid> type (stable logical encoding: List<UUID>). */
  static final class ListUuidUserType implements UserType<List<UUID>> {
    @Override public String id() { return "list<uuid>"; }
    @SuppressWarnings("unchecked")
    @Override public Class<List<UUID>> javaType() { return (Class<List<UUID>>) (Class<?>) List.class; }

    @Override
    public List<UUID> decode(Object raw) {
      if (raw == null) return null;
      if (raw instanceof Object[] oa) return toUuidList(Arrays.asList(oa));
      if (raw instanceof List<?> l) return toUuidList(l);
      if (raw instanceof String s) {
        try {
          List<?> l = JSON.readValue(s, JSON.getTypeFactory().constructCollectionType(List.class, Object.class));
          return toUuidList(l);
        } catch (Exception e) {
          return List.of(toUuid(raw));
        }
      }
      return List.of(toUuid(raw));
    }

    @Override
    public Object encode(List<UUID> value) {
      if (value == null) return null;
      List<UUID> out = new ArrayList<>(value.size());
      for (Object x : value) out.add(toUuid(x));
      return out;
    }

    private static List<UUID> toUuidList(Iterable<?> it) {
      List<UUID> out = new ArrayList<>();
      for (Object x : it) out.add(toUuid(x));
      return out;
    }

    private static UUID toUuid(Object x) {
      if (x == null) return null;
      if (x instanceof UUID u) return u;
      String s = String.valueOf(x).trim();
      if (s.isEmpty()) return null;
      return UUID.fromString(s);
    }
  }

  /** Global logical list<double> type (stable logical encoding: List<Double>). */
  static final class ListDoubleUserType implements UserType<List<Double>> {
    @Override public String id() { return "list<double>"; }
    @SuppressWarnings("unchecked")
    @Override public Class<List<Double>> javaType() { return (Class<List<Double>>) (Class<?>) List.class; }

    @Override
    public List<Double> decode(Object raw) {
      if (raw == null) return null;
      if (raw instanceof Object[] oa) return toDoubleList(Arrays.asList(oa));
      if (raw instanceof List<?> l) return toDoubleList(l);
      if (raw instanceof String s) {
        try {
          List<?> l = JSON.readValue(s, JSON.getTypeFactory().constructCollectionType(List.class, Object.class));
          return toDoubleList(l);
        } catch (Exception e) {
          return List.of(toDouble(raw));
        }
      }
      return List.of(toDouble(raw));
    }

    @Override
    public Object encode(List<Double> value) {
      if (value == null) return null;
      List<Double> out = new ArrayList<>(value.size());
      for (Object x : value) out.add(toDouble(x));
      return out;
    }

    private static List<Double> toDoubleList(Iterable<?> it) {
      List<Double> out = new ArrayList<>();
      for (Object x : it) out.add(toDouble(x));
      return out;
    }

    private static Double toDouble(Object x) {
      if (x == null) return null;
      if (x instanceof Double d) return d;
      if (x instanceof Number n) return n.doubleValue();
      if (x instanceof String s) return s.isBlank() ? null : Double.valueOf(s.trim());
      return Double.valueOf(String.valueOf(x));
    }
  }
}


