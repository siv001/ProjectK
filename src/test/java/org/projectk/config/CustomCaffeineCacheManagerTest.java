package org.projectk.config;

import com.github.benmanes.caffeine.cache.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CustomCaffeineCacheManagerTest {

    private CustomCaffeineCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new CustomCaffeineCacheManager();
    }

    @Test
    void testRegisterAndCreateCache() {
        String cacheName = "testCache";

        // Create a Caffeine builder with specific settings
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(100);

        // Create a CacheLoader
        CacheLoader<Object, Object> loader = key -> "value-for-" + key;

        // Register the cache
        cacheManager.registerCache(cacheName, builder, loader);

        // Retrieve the cache
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "Cache should not be null");

        // Verify cache behavior
        Object value = cache.get("key");
        assertEquals("value-for-key", value, "Cache should return the correct value");
    }

    @Test
    void testFallbackToDefaultBehavior() {
        String cacheName = "defaultCache";

        // Retrieve a cache without registering it
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "Cache should not be null");

        // Verify default behavior (null loader)
        Object value = cache.get("key");
        assertEquals("default-value", value, "Cache should return the default value");
    }
}
