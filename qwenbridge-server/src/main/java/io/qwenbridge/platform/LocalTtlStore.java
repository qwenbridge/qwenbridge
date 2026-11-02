package io.qwenbridge.platform;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded in-process key/value and list storage with expiry (QB-205), for single-instance
 * deployments and tests that run without Redis ({@code qwenbridge.platform.store=memory}). The
 * oldest entries are evicted beyond {@code maxEntries}; nothing survives a restart or is shared
 * between instances.
 */
public final class LocalTtlStore {
  private record Timed(String value, Instant expires) {}

  private final Map<String, Timed> values;
  private final Map<String, Deque<String>> lists;
  private final Clock clock;

  public LocalTtlStore(int maxEntries, Clock clock) {
    if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
    this.clock = clock;
    this.values =
        new LinkedHashMap<>(16, 0.75f, false) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Timed> eldest) {
            return size() > maxEntries;
          }
        };
    this.lists =
        new LinkedHashMap<>(16, 0.75f, false) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Deque<String>> eldest) {
            return size() > maxEntries;
          }
        };
  }

  public synchronized String get(String key) {
    Timed entry = values.get(key);
    if (entry == null) return null;
    if (!entry.expires().isAfter(clock.instant())) {
      values.remove(key);
      return null;
    }
    return entry.value();
  }

  public synchronized void set(String key, String value, Duration ttl) {
    values.remove(key);
    values.put(key, new Timed(value, clock.instant().plus(ttl)));
  }

  /** Stores the value only when the key is absent (or expired); true when it was stored. */
  public synchronized boolean setIfAbsent(String key, String value, Duration ttl) {
    if (get(key) != null) return false;
    set(key, value, ttl);
    return true;
  }

  /** Prepends {@code value}, keeping at most {@code max} newest elements. */
  public synchronized void push(String key, String value, int max) {
    Deque<String> list = lists.computeIfAbsent(key, k -> new ArrayDeque<>());
    list.addFirst(value);
    while (list.size() > max) list.removeLast();
  }

  /** The newest {@code count} elements, newest first. */
  public synchronized List<String> range(String key, int count) {
    Deque<String> list = lists.get(key);
    if (list == null) return List.of();
    var out = new ArrayList<String>(Math.min(count, list.size()));
    for (String value : list) {
      if (out.size() == count) break;
      out.add(value);
    }
    return out;
  }
}
