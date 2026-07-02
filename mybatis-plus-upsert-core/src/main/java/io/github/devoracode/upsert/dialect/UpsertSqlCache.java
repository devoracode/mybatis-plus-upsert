package io.github.devoracode.upsert.dialect;

import java.util.concurrent.ConcurrentHashMap;

public final class UpsertSqlCache {

    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private UpsertSqlCache() {
    }

    static String get(String key, SqlProvider provider) {
        return CACHE.computeIfAbsent(key, provider::provide);
    }

    public static void clear() {
        CACHE.clear();
    }

    public static boolean containsKey(String key) {
        return CACHE.containsKey(key);
    }

    @FunctionalInterface
    interface SqlProvider {
        String provide(String key);
    }
}
