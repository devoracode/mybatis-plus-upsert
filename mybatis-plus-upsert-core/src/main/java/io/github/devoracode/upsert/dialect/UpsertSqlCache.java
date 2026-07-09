package io.github.devoracode.upsert.dialect;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for generated upsert SQL strings.
 * SQL fragments are stored by a composite key (dialect class name + table name + single/batch)
 * and never evicted. This is intentional: entity classes are typically finite and
 * the cache is cleared on application shutdown.
 *
 * @author devoracode
 * @since 1.0.0
 */
public final class UpsertSqlCache {

    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private UpsertSqlCache() {
    }

    /**
     * Gets the SQL string for the given key, computing it lazily if absent.
     *
     * @param key     the cache key (must not be null)
     * @param provider the supplier that computes the SQL if the key is absent (must not be null)
     * @return the cached or newly computed SQL string
     */
    static String get(String key, SqlProvider provider) {
        return CACHE.computeIfAbsent(key, provider::provide);
    }

    /**
     * Clears all cached SQL strings. Primarily intended for testing.
     */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * Checks whether the given key exists in the cache.
     *
     * @param key the cache key to check (must not be null)
     * @return true if the key is present in the cache
     */
    public static boolean containsKey(String key) {
        return CACHE.containsKey(key);
    }

    /**
     * Functional interface for SQL providers used by the cache.
     */
    @FunctionalInterface
    interface SqlProvider {
        /**
         * Provides the SQL string for the given key.
         *
         * @param key the cache key
         * @return the generated SQL string
         */
        String provide(String key);
    }
}