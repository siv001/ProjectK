package org.projectk.config;

import com.github.benmanes.caffeine.cache.*;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.lang.NonNull;

import java.util.*;

public class CustomCaffeineCacheManager extends CaffeineCacheManager {
    private final Map<String, Caffeine<Object, Object>> builders = new HashMap<>();
    private final Map<String, CacheLoader<Object, Object>> loaders  = new HashMap<>();

    /**
     * Register a cache by name, with its builder (expire/refresh)
     * and a loader to actually fetch new data.
     * 
     * @param name The name of the cache (must not be null)
     * @param builder The Caffeine builder for cache configuration (can be null)
     * @param loader The CacheLoader for loading cache values (can be null)
     * @throws NullPointerException if name is null
     * @throws IllegalArgumentException if name is empty
     */
    public void registerCache(@NonNull String name,
                            Caffeine<Object, Object> builder,
                            CacheLoader<Object, Object> loader) {
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Cache name must not be empty");
        }
        
        builders.put(name, builder);
        loaders.put(name, loader);
        setCacheNames(loaders.keySet());
    }
    
    /**
     * Gets the cache loader for a specific cache name
     * 
     * @param cacheName The name of the cache
     * @return The cache loader associated with the cache, or null if none exists
     */
    public CacheLoader<Object, Object> getLoader(String cacheName) {
        return loaders.get(cacheName);
    }

    @Override
    @NonNull
    protected Cache createCaffeineCache(@NonNull String name) {
        Caffeine<Object, Object> builder = builders.get(name);
        CacheLoader<Object, Object> loader = loaders.get(name);

        if (builder != null && loader != null) {
            // build a LoadingCache so that refreshAfterWrite works
            LoadingCache<Object, Object> loadingCache = builder.build(loader);
            return new CaffeineCache(name, loadingCache, true); // Allow null values
        }

        // fallback to default behavior (no refresh support)
        return super.createCaffeineCache(name);
    }
}
