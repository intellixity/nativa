package io.intellixity.nativa.persistence.authoring;

import io.intellixity.nativa.persistence.util.NativaFactoriesLoader;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UserTypeRegistry built via discovery (META-INF/nativa.factories).\n
 *
 * Lookup order:\n
 * - dialect-specific provider types (by UserType.id)\n
 * - global provider types (dialectId="*")\n
 * - synthesized container types for list<...>/set<...>/map<...,...>/array<...>\n
 * - error\n
 */
public final class DiscoveredUserTypeRegistry implements UserTypeRegistry {
  public static final String GLOBAL_DIALECT = "*";

  private final String dialectId;
  private final Map<String, UserType<?>> dialectTypes;
  private final Map<String, UserType<?>> globalTypes;
  private final Map<String, UserType<?>> synthesized = new ConcurrentHashMap<>();

  public DiscoveredUserTypeRegistry(String dialectId) {
    this(dialectId, NativaFactoriesLoader.load(UserTypeProvider.class));
  }

  DiscoveredUserTypeRegistry(String dialectId, List<UserTypeProvider> providers) {
    this.dialectId = (dialectId == null || dialectId.isBlank()) ? "" : dialectId;
    Map<String, UserType<?>> dialect = new LinkedHashMap<>();
    Map<String, UserType<?>> global = new LinkedHashMap<>();

    for (UserTypeProvider p : providers) {
      if (p == null) continue;
      String did = normalizeDialect(p.dialectId());
      Collection<UserType<?>> types = p.userTypes();
      if (types == null) continue;
      for (UserType<?> ut : types) {
        if (ut == null) continue;
        if (GLOBAL_DIALECT.equals(did)) {
          global.putIfAbsent(ut.id(), ut);
        } else if (Objects.equals(this.dialectId, did)) {
          // dialect-specific override wins (keep first discovered for determinism)
          dialect.putIfAbsent(ut.id(), ut);
        }
      }
    }

    this.dialectTypes = Map.copyOf(dialect);
    this.globalTypes = Map.copyOf(global);
  }

  @Override
  public UserType<?> get(String userTypeId) {
    if (userTypeId == null || userTypeId.isBlank()) {
      throw new IllegalArgumentException("Unknown userTypeId: " + userTypeId);
    }
    UserType<?> t = dialectTypes.get(userTypeId);
    if (t != null) return t;
    t = globalTypes.get(userTypeId);
    if (t != null) return t;

    // container synthesis (only if no explicit type registered)
    UserType<?> syn = synthesized.computeIfAbsent(userTypeId, this::trySynthesize);
    if (syn != null) return syn;

    throw new IllegalArgumentException("Unknown userTypeId: " + userTypeId + " (dialectId=" + dialectId + ")");
  }

  private UserType<?> trySynthesize(String userTypeId) {
    // list<...>
    if (userTypeId.startsWith("list<") && userTypeId.endsWith(">")) {
      String elId = userTypeId.substring("list<".length(), userTypeId.length() - 1);
      @SuppressWarnings("unchecked")
      UserType<Object> el = (UserType<Object>) get(elId);
      return new ListType(userTypeId, el);
    }
    // set<...>
    if (userTypeId.startsWith("set<") && userTypeId.endsWith(">")) {
      String elId = userTypeId.substring("set<".length(), userTypeId.length() - 1);
      @SuppressWarnings("unchecked")
      UserType<Object> el = (UserType<Object>) get(elId);
      return new SetType(userTypeId, el);
    }
    // array<...>
    if (userTypeId.startsWith("array<") && userTypeId.endsWith(">")) {
      String elId = userTypeId.substring("array<".length(), userTypeId.length() - 1);
      @SuppressWarnings("unchecked")
      UserType<Object> el = (UserType<Object>) get(elId);
      return new ArrayType(userTypeId, el);
    }
    // map<k,v>
    if (userTypeId.startsWith("map<") && userTypeId.endsWith(">")) {
      String body = userTypeId.substring("map<".length(), userTypeId.length() - 1);
      int comma = body.indexOf(',');
      if (comma <= 0 || comma >= body.length() - 1) return null;
      String keyId = body.substring(0, comma);
      String valId = body.substring(comma + 1);
      @SuppressWarnings("unchecked")
      UserType<Object> kt = (UserType<Object>) get(keyId);
      @SuppressWarnings("unchecked")
      UserType<Object> vt = (UserType<Object>) get(valId);
      return new MapType(userTypeId, kt, vt);
    }
    return null;
  }

  private static String normalizeDialect(String did) {
    if (did == null) return GLOBAL_DIALECT;
    String s = did.trim();
    return s.isEmpty() ? GLOBAL_DIALECT : s;
  }

  // ---- synthesized container types ----

  static final class ListType implements UserType<List<?>> {
    private final String id;
    private final UserType<Object> el;

    ListType(String id, UserType<Object> el) {
      this.id = id;
      this.el = el;
    }

    @Override public String id() { return id; }
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override public Class<List<?>> javaType() { return (Class) List.class; }

    @Override
    public List<?> decode(Object raw) {
      if (raw == null) return null;
      Iterable<?> it;
      if (raw instanceof List<?> l) it = l;
      else if (raw instanceof Object[] oa) it = Arrays.asList(oa);
      else {
        it = List.of(raw);
      }
      List<Object> out = new ArrayList<>();
      for (Object o : it) out.add(el.decode(o));
      return out;
    }

    @Override
    public Object encode(List<?> value) {
      if (value == null) return null;
      List<Object> out = new ArrayList<>(value.size());
      for (Object o : value) out.add(el.encode(o));
      return out;
    }
  }

  static final class SetType implements UserType<Set<?>> {
    private final String id;
    private final UserType<Object> el;

    SetType(String id, UserType<Object> el) {
      this.id = id;
      this.el = el;
    }

    @Override public String id() { return id; }
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override public Class<Set<?>> javaType() { return (Class) Set.class; }

    @Override
    public Set<?> decode(Object raw) {
      if (raw == null) return null;
      Collection<?> c;
      if (raw instanceof Set<?> s) c = s;
      else if (raw instanceof List<?> l) c = l;
      else if (raw instanceof Object[] oa) c = Arrays.asList(oa);
      else c = List.of(raw);
      Set<Object> out = new LinkedHashSet<>();
      for (Object o : c) out.add(el.decode(o));
      return out;
    }

    @Override
    public Object encode(Set<?> value) {
      if (value == null) return null;
      List<Object> out = new ArrayList<>(value.size());
      for (Object o : value) out.add(el.encode(o));
      return out;
    }
  }

  static final class ArrayType implements UserType<Object[]> {
    private final String id;
    private final UserType<Object> el;

    ArrayType(String id, UserType<Object> el) {
      this.id = id;
      this.el = el;
    }

    @Override public String id() { return id; }
    @Override public Class<Object[]> javaType() { return Object[].class; }

    @Override
    public Object[] decode(Object raw) {
      if (raw == null) return null;
      if (raw instanceof Object[] oa) {
        Object[] out = new Object[oa.length];
        for (int i = 0; i < oa.length; i++) out[i] = el.decode(oa[i]);
        return out;
      }
      return new Object[] { el.decode(raw) };
    }

    @Override
    public Object encode(Object[] value) {
      if (value == null) return null;
      Object[] out = new Object[value.length];
      for (int i = 0; i < value.length; i++) out[i] = el.encode(value[i]);
      return out;
    }
  }

  static final class MapType implements UserType<Map<?, ?>> {
    private final String id;
    private final UserType<Object> key;
    private final UserType<Object> val;

    MapType(String id, UserType<Object> key, UserType<Object> val) {
      this.id = id;
      this.key = key;
      this.val = val;
    }

    @Override public String id() { return id; }
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override public Class<Map<?, ?>> javaType() { return (Class) Map.class; }

    @Override
    public Map<?, ?> decode(Object raw) {
      if (raw == null) return null;
      if (!(raw instanceof Map<?, ?> m)) throw new IllegalArgumentException("Not a map: " + raw.getClass());
      Map<Object, Object> out = new LinkedHashMap<>();
      for (var e : m.entrySet()) out.put(key.decode(e.getKey()), val.decode(e.getValue()));
      return out;
    }

    @Override
    public Object encode(Map<?, ?> value) {
      if (value == null) return null;
      Map<Object, Object> out = new LinkedHashMap<>();
      for (var e : value.entrySet()) out.put(key.encode(e.getKey()), val.encode(e.getValue()));
      return out;
    }
  }

  // ---- useful global scalar types for providers ----

  public static final class GlobalTypes {
    private GlobalTypes() {}

    public static UserType<String> string() {
      return new UserType<>() {
        @Override public String id() { return "string"; }
        @Override public Class<String> javaType() { return String.class; }
        @Override public String decode(Object raw) { return raw == null ? null : String.valueOf(raw); }
        @Override public Object encode(String value) { return value; }
      };
    }

    public static UserType<Integer> integer() {
      return new UserType<>() {
        @Override public String id() { return "int"; }
        @Override public Class<Integer> javaType() { return Integer.class; }
        @Override public Integer decode(Object raw) {
          if (raw == null) return null;
          if (raw instanceof Number n) return n.intValue();
          return Integer.parseInt(String.valueOf(raw));
        }
        @Override public Object encode(Integer value) { return value; }
      };
    }

    public static UserType<Long> longType() {
      return new UserType<>() {
        @Override public String id() { return "long"; }
        @Override public Class<Long> javaType() { return Long.class; }
        @Override public Long decode(Object raw) {
          if (raw == null) return null;
          if (raw instanceof Number n) return n.longValue();
          return Long.parseLong(String.valueOf(raw));
        }
        @Override public Object encode(Long value) { return value; }
      };
    }

    public static UserType<Boolean> bool() {
      return new UserType<>() {
        @Override public String id() { return "bool"; }
        @Override public Class<Boolean> javaType() { return Boolean.class; }
        @Override public Boolean decode(Object raw) {
          if (raw == null) return null;
          if (raw instanceof Boolean b) return b;
          return Boolean.parseBoolean(String.valueOf(raw));
        }
        @Override public Object encode(Boolean value) { return value; }
      };
    }

    public static UserType<Double> doubleType() {
      return new UserType<>() {
        @Override public String id() { return "double"; }
        @Override public Class<Double> javaType() { return Double.class; }
        @Override public Double decode(Object raw) {
          if (raw == null) return null;
          if (raw instanceof Number n) return n.doubleValue();
          return Double.parseDouble(String.valueOf(raw));
        }
        @Override public Object encode(Double value) { return value; }
      };
    }

    public static UserType<UUID> uuid() {
      return new UserType<>() {
        @Override public String id() { return "uuid"; }
        @Override public Class<UUID> javaType() { return UUID.class; }
        @Override public UUID decode(Object raw) {
          if (raw == null) return null;
          if (raw instanceof UUID u) return u;
          return UUID.fromString(String.valueOf(raw));
        }
        @Override public Object encode(UUID value) { return value; }
      };
    }

    public static UserType<Instant> instant() {
      return new UserType<>() {
        @Override public String id() { return "instant"; }
        @Override public Class<Instant> javaType() { return Instant.class; }
        @Override public Instant decode(Object raw) {
          if (raw == null) return null;
          if (raw instanceof Instant i) return i;
          if (raw instanceof java.util.Date d) return d.toInstant();
          return Instant.parse(String.valueOf(raw));
        }
        @Override public Object encode(Instant value) { return value; }
      };
    }

    /** Identity JSON type (useful for document backends like Mongo). */
    public static UserType<Object> jsonIdentity() {
      return new UserType<>() {
        @Override public String id() { return "json"; }
        @Override public Class<Object> javaType() { return Object.class; }
        @Override public Object decode(Object raw) { return raw; }
        @Override public Object encode(Object value) { return value; }
      };
    }
  }
}


