package org.projectk.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectk.dto.ServiceOneResponse;
import org.projectk.dto.ServiceTwoResponse;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

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
        assertTrue(cacheManager instanceof CustomCaffeineCacheManager);

        CaffeineCache serviceOneCache = (CaffeineCache) cacheManager.getCache("serviceOneCache");
        assertNotNull(serviceOneCache);

        CaffeineCache serviceTwoCache = (CaffeineCache) cacheManager.getCache("serviceTwoCache");
        assertNotNull(serviceTwoCache);
    }

    @Test
    void testServiceOneCacheLoader() {
        // Prepare mock response
        ServiceOneResponse mockResponse = new ServiceOneResponse();
        mockResponse.setData("value1");
        mockResponse.setTtl(3600);
        
        when(serviceOneClient.fetchFresh("key1")).thenReturn(mockResponse);

        CacheManager cacheManager = cacheConfig.cacheManager(serviceOneClient, serviceTwoClient);
        CaffeineCache serviceOneCache = (CaffeineCache) cacheManager.getCache("serviceOneCache");

        assertNotNull(serviceOneCache);
        ServiceOneResponse result = serviceOneCache.get("key1", () -> serviceOneClient.fetchFresh("key1"));
        assertNotNull(result);
        assertEquals("value1", result.getData());
        verify(serviceOneClient, times(1)).fetchFresh("key1");
    }

    @Test
    void testServiceTwoCacheLoader() {
        // Prepare mock response
        ServiceTwoResponse mockResponse = new ServiceTwoResponse();
        mockResponse.setData("value2");
        mockResponse.setTtl(7200);
        
        when(serviceTwoClient.fetchFresh("key2")).thenReturn(mockResponse);

        CacheManager cacheManager = cacheConfig.cacheManager(serviceOneClient, serviceTwoClient);
        CaffeineCache serviceTwoCache = (CaffeineCache) cacheManager.getCache("serviceTwoCache");

        assertNotNull(serviceTwoCache);
        ServiceTwoResponse result = serviceTwoCache.get("key2", () -> serviceTwoClient.fetchFresh("key2"));
        assertNotNull(result);
        assertEquals("value2", result.getData());
        verify(serviceTwoClient, times(1)).fetchFresh("key2");
    }
}