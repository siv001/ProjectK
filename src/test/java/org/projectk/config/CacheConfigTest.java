package org.projectk.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectk.dto.ServiceOneResponse;
import org.projectk.dto.ServiceTwoResponse;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheConfigTest {

    private ServiceOneClient serviceOneClient;
    private ServiceTwoClient serviceTwoClient;
    private CacheConfig cacheConfig;

    @BeforeEach
    void setUp() {
        serviceOneClient = mock(ServiceOneClient.class);
        serviceTwoClient = mock(ServiceTwoClient.class);
        cacheConfig = new CacheConfig();
    }

    @Test
    void testCacheManagerConfiguration() {
        CacheManager cacheManager = cacheConfig.cacheManager(serviceOneClient, serviceTwoClient);

        assertNotNull(cacheManager);
        assertTrue(cacheManager instanceof CaffeineCacheManager);

        CaffeineCache serviceOneCache = (CaffeineCache) cacheManager.getCache("serviceOneCache");
        assertNotNull(serviceOneCache);

        CaffeineCache serviceTwoCache = (CaffeineCache) cacheManager.getCache("serviceTwoCache");
        assertNotNull(serviceTwoCache);
    }

    @Test
    void testServiceOneCacheLoader() {
        when(serviceOneClient.fetchFresh("key1")).thenReturn(new ServiceOneResponse());

        CacheManager cacheManager = cacheConfig.cacheManager(serviceOneClient, serviceTwoClient);
        CaffeineCache serviceOneCache = (CaffeineCache) cacheManager.getCache("serviceOneCache");

        assertNotNull(serviceOneCache);
        assertEquals("value1", serviceOneCache.get("key1", () -> serviceOneClient.fetchFresh("key1")));
        verify(serviceOneClient, times(1)).fetchFresh("key1");
    }

    @Test
    void testServiceTwoCacheLoader() {
        when(serviceTwoClient.fetchFresh("key2")).thenReturn(new ServiceTwoResponse());

        CacheManager cacheManager = cacheConfig.cacheManager(serviceOneClient, serviceTwoClient);
        CaffeineCache serviceTwoCache = (CaffeineCache) cacheManager.getCache("serviceTwoCache");

        assertNotNull(serviceTwoCache);
        assertEquals("value2", serviceTwoCache.get("key2", () -> serviceTwoClient.fetchFresh("key2")));
        verify(serviceTwoClient, times(1)).fetchFresh("key2");
    }
}