package io.intellixity.nativa.persistence.mapping;

import java.util.*;
import java.util.function.*;

public final class Coercions {
  private Coercions() {}

  public static <T> List<T> toList(Iterable<Object> raw, Function<Object,T> decode) {
    if (raw == null) return null;
    List<T> out = new ArrayList<>();
    for (Object o : raw) out.add(decode.apply(o));
    return out;
  }

  public static <T> Set<T> toSet(Iterable<Object> raw, Function<Object,T> decode) {
    if (raw == null) return null;
    Set<T> out = new LinkedHashSet<>();
    for (Object o : raw) out.add(decode.apply(o));
    return out;
  }

  @SuppressWarnings("unchecked")
  public static <K,V> Map<K,V> toMap(Object raw, Function<Object,K> dk, Function<Object,V> dv) {
    if (raw == null) return null;
    if (raw instanceof Map<?,?> m) {
      Map<K,V> out = new LinkedHashMap<>();
      for (var e : m.entrySet()) out.put(dk.apply(e.getKey()), dv.apply(e.getValue()));
      return out;
    }
    throw new IllegalArgumentException("Expected map but got: " + raw.getClass());
  }

  public static <T> T[] toArray(Iterable<Object> raw, IntFunction<T[]> factory, Function<Object,T> decode) {
    if (raw == null) return null;
    List<T> list = new ArrayList<>();
    for (Object o : raw) list.add(decode.apply(o));
    return list.toArray(factory.apply(list.size()));
  }
}

