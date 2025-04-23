package org.projectk.config;

import com.github.benmanes.caffeine.cache.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;

import java.util.concurrent.TimeUnit;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class CustomCaffeineCacheManagerTest {

    private CustomCaffeineCacheManager cacheManager;
    private Caffeine<Object, Object> defaultBuilder;
    private CacheLoader<Object, Object> defaultLoader;

    @BeforeEach
    void setUp() {
        cacheManager = new CustomCaffeineCacheManager();
        defaultBuilder = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(100);
        defaultLoader = key -> "value-for-" + key;
    }

    @Test
    void testRegisterAndCreateCache() {
        String cacheName = "testCache";
        cacheManager.registerCache(cacheName, defaultBuilder, defaultLoader);

        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "Cache should not be null");

        ValueWrapper valueWrapper = cache.get("key");
        assertNotNull(valueWrapper, "Value wrapper should not be null");
        assertEquals("value-for-key", valueWrapper.get(), "Cache should return the loaded value");
    }

    @Test
    void testFallbackToDefaultBehavior() {
        String cacheName = "defaultCache";
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "Cache should not be null");

        cache.put("key", "default-value");
        ValueWrapper valueWrapper = cache.get("key");
        assertNotNull(valueWrapper, "Value wrapper should not be null");
        assertEquals("default-value", valueWrapper.get(), "Cache should return the stored value");
    }

    @Test
    void testMultipleCacheRegistration() {
        String firstCacheName = "firstCache";
        String secondCacheName = "secondCache";
        
        Caffeine<Object, Object> firstBuilder = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES);
        CacheLoader<Object, Object> firstLoader = key -> "first-" + key;
        
        Caffeine<Object, Object> secondBuilder = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES);
        CacheLoader<Object, Object> secondLoader = key -> "second-" + key;
        
        cacheManager.registerCache(firstCacheName, firstBuilder, firstLoader);
        cacheManager.registerCache(secondCacheName, secondBuilder, secondLoader);

        Cache firstCache = cacheManager.getCache(firstCacheName);
        Cache secondCache = cacheManager.getCache(secondCacheName);

        assertNotNull(firstCache, "First cache should not be null");
        assertNotNull(secondCache, "Second cache should not be null");

        ValueWrapper firstValue = firstCache.get("testKey");
        ValueWrapper secondValue = secondCache.get("testKey");
        
        assertNotNull(firstValue, "First cache value should not be null");
        assertNotNull(secondValue, "Second cache value should not be null");
        assertEquals("first-testKey", firstValue.get(), "First cache should use its loader");
        assertEquals("second-testKey", secondValue.get(), "Second cache should use its loader");
    }

    @Test
    void testNullValuesHandling() {
        String cacheName = "nullCache";
        CacheLoader<Object, Object> nullLoader = key -> null;
        cacheManager.registerCache(cacheName, defaultBuilder, nullLoader);
        
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "Cache should not be null");
        
        // When using LoadingCache with a loader that returns null,
        // the cache.get() will return null instead of a ValueWrapper
        assertNull(cache.get("key"), "Cache should return null for null values");
        
        // But we can still put and get null values directly
        cache.put("key", null);
        ValueWrapper valueWrapper = cache.get("key");
        assertNotNull(valueWrapper, "Value wrapper should not be null");
        assertNull(valueWrapper.get(), "Cache should handle null values correctly");
    }

    @Test
    void testCacheNamesSet() {
        String cacheName1 = "cache1";
        String cacheName2 = "cache2";

        cacheManager.registerCache(cacheName1, defaultBuilder, defaultLoader);
        cacheManager.registerCache(cacheName2, defaultBuilder, defaultLoader);

        Collection<String> cacheNames = cacheManager.getCacheNames();
        assertTrue(cacheNames.contains(cacheName1), "Cache names should contain first cache");
        assertTrue(cacheNames.contains(cacheName2), "Cache names should contain second cache");
        assertEquals(2, cacheNames.size(), "Should have exactly two caches registered");
    }

    @Test
    void testRegisterCacheWithNullBuilder() {
        String cacheName = "nullBuilderCache";
        cacheManager.registerCache(cacheName, null, defaultLoader);
        
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "Cache should not be null");
        
        // Should fall back to default behavior
        cache.put("key", "value");
        ValueWrapper valueWrapper = cache.get("key");
        assertNotNull(valueWrapper, "Value wrapper should not be null");
        assertEquals("value", valueWrapper.get(), "Cache should use default behavior");
    }

    @Test
    void testRegisterCacheWithNullLoader() {
        String cacheName = "nullLoaderCache";
        cacheManager.registerCache(cacheName, defaultBuilder, null);
        
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "Cache should not be null");
        
        // Should fall back to default behavior
        cache.put("key", "value");
        ValueWrapper valueWrapper = cache.get("key");
        assertNotNull(valueWrapper, "Value wrapper should not be null");
        assertEquals("value", valueWrapper.get(), "Cache should use default behavior");
    }

    @Test
    void testRegisterCacheWithEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> {
            cacheManager.registerCache("", defaultBuilder, defaultLoader);
        }, "Should throw IllegalArgumentException for empty cache name");
    }

    @Test
    @SuppressWarnings("null") // We're testing null handling
    void testRegisterCacheWithNullName() {
        assertThrows(NullPointerException.class, () -> {
            cacheManager.registerCache(null, defaultBuilder, defaultLoader);
        }, "Should throw NullPointerException for null cache name");
    }

    @Test
    void testOverrideExistingCache() {
        String cacheName = "overrideCache";
        CacheLoader<Object, Object> firstLoader = key -> "first-" + key;
        CacheLoader<Object, Object> secondLoader = key -> "second-" + key;
        
        // Register first cache
        cacheManager.registerCache(cacheName, defaultBuilder, firstLoader);
        Cache firstCache = cacheManager.getCache(cacheName);
        assertNotNull(firstCache, "First cache should not be null");
        
        ValueWrapper firstValue = firstCache.get("key");
        assertNotNull(firstValue, "First value wrapper should not be null");
        assertEquals("first-key", firstValue.get(), "Should use first loader");
        
        // Override with second cache
        cacheManager.registerCache(cacheName, defaultBuilder, secondLoader);
        Cache secondCache = cacheManager.getCache(cacheName);
        assertNotNull(secondCache, "Second cache should not be null");
        
        ValueWrapper secondValue = secondCache.get("key");
        assertNotNull(secondValue, "Second value wrapper should not be null");
        assertEquals("second-key", secondValue.get(), "Should use second loader");
    }

    @Test
    void testCreateCacheWithMissingBuilder() {
        String cacheName = "missingBuilderCache";
        cacheManager.registerCache(cacheName, null, defaultLoader);
        
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "Cache should not be null");
        
        // Should fall back to default behavior
        cache.put("key", "value");
        ValueWrapper valueWrapper = cache.get("key");
        assertNotNull(valueWrapper, "Value wrapper should not be null");
        assertEquals("value", valueWrapper.get(), "Cache should use default behavior");
    }

    @Test
    void testCreateCacheWithMissingLoader() {
        String cacheName = "missingLoaderCache";
        cacheManager.registerCache(cacheName, defaultBuilder, null);
        
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "Cache should not be null");
        
        // Should fall back to default behavior
        cache.put("key", "value");
        ValueWrapper valueWrapper = cache.get("key");
        assertNotNull(valueWrapper, "Value wrapper should not be null");
        assertEquals("value", valueWrapper.get(), "Cache should use default behavior");
    }
}
