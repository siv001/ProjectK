package org.projectk.config;

import com.github.benmanes.caffeine.cache.*;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.*;

public class CustomCaffeineCacheManager extends CaffeineCacheManager {
    private final Map<String, Caffeine<Object, Object>> builders = new HashMap<>();
    private final Map<String, CacheLoader<Object, Object>> loaders  = new HashMap<>();

    /**
     * Register a cache by name, with its builder (expire/refresh)
     * and a loader to actually fetch new data.
     */
    public void registerCache(String name,
                              Caffeine<Object, Object> builder,
                              CacheLoader<Object, Object> loader) {
        builders.put(name, builder);
        loaders.put(name, loader);
        setCacheNames(loaders.keySet());
    }

    @Override
    protected Cache createCaffeineCache(String name) {
        Caffeine<Object, Object> builder = builders.get(name);
        CacheLoader<Object, Object> loader  = loaders.get(name);

        if (builder != null && loader != null) {
            // build a LoadingCache so that refreshAfterWrite works
            LoadingCache<Object, Object> loadingCache = builder.build(loader);
            return new CaffeineCache(name, loadingCache, isAllowNullValues());
        }

        // fallback to default behavior (no refresh support)
        return super.createCaffeineCache(name);
    }
}

