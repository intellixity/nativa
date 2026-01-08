package io.intellixity.nativa.persistence.governance.internal;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Simple synchronized LRU cache with TTL + optional idle expiry.\n
 *
 * - LRU eviction: access-order LinkedHashMap\n
 * - TTL: expire-after-write\n
 * - Idle: expire-after-access (optional)\n
 */
public final class LruTtlCache<K, V> {
  private final int maxEntries;
  private final long ttlMillis;
  private final long idleMillis;
  private final LongSupplier nowMillis;

  private final LinkedHashMap<K, Entry<V>> map = new LinkedHashMap<>(16, 0.75f, true);

  private static final class Entry<V> {
    final V value;
    long writeAt;
    long accessAt;

    Entry(V value, long now) {
      this.value = value;
      this.writeAt = now;
      this.accessAt = now;
    }
  }

  public LruTtlCache(int maxEntries, long ttlMillis, long idleMillis) {
    this(maxEntries, ttlMillis, idleMillis, System::currentTimeMillis);
  }

  public LruTtlCache(int maxEntries, long ttlMillis, long idleMillis, LongSupplier nowMillis) {
    if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be > 0");
    if (ttlMillis < 0) throw new IllegalArgumentException("ttlMillis must be >= 0");
    if (idleMillis < 0) throw new IllegalArgumentException("idleMillis must be >= 0");
    this.maxEntries = maxEntries;
    this.ttlMillis = ttlMillis;
    this.idleMillis = idleMillis;
    this.nowMillis = Objects.requireNonNull(nowMillis, "nowMillis");
  }

  public synchronized V get(K key) {
    Objects.requireNonNull(key, "key");
    long now = nowMillis.getAsLong();
    pruneExpired(now);
    Entry<V> e = map.get(key);
    if (e == null) return null;
    if (isExpired(e, now)) {
      map.remove(key);
      return null;
    }
    e.accessAt = now;
    return e.value;
  }

  public synchronized V put(K key, V value) {
    Objects.requireNonNull(key, "key");
    long now = nowMillis.getAsLong();
    pruneExpired(now);
    Entry<V> prev = map.put(key, new Entry<>(value, now));
    evictIfNeeded();
    return prev == null ? null : prev.value;
  }

  public synchronized V getOrCompute(K key, Supplier<V> supplier) {
    Objects.requireNonNull(supplier, "supplier");
    V existing = get(key);
    if (existing != null) return existing;
    V created = supplier.get();
    put(key, created);
    return created;
  }

  public synchronized int size() {
    pruneExpired(nowMillis.getAsLong());
    return map.size();
  }

  private boolean isExpired(Entry<V> e, long now) {
    if (ttlMillis > 0 && (now - e.writeAt) >= ttlMillis) return true;
    if (idleMillis > 0 && (now - e.accessAt) >= idleMillis) return true;
    return false;
  }

  private void pruneExpired(long now) {
    if (map.isEmpty()) return;
    Iterator<Map.Entry<K, Entry<V>>> it = map.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<K, Entry<V>> me = it.next();
      if (isExpired(me.getValue(), now)) it.remove();
    }
  }

  private void evictIfNeeded() {
    while (map.size() > maxEntries) {
      Iterator<Map.Entry<K, Entry<V>>> it = map.entrySet().iterator();
      if (!it.hasNext()) return;
      it.next();
      it.remove();
    }
  }
}




