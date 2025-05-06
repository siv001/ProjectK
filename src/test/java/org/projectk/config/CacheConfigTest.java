package org.projectk.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.TaskScheduler;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;

import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CacheConfigTest {

    @Mock
    private ServiceOneClient serviceOneClient;

    @Mock
    private ServiceTwoClient serviceTwoClient;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    private CacheConfig cacheConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cacheConfig = new CacheConfig();
    }

    @Test
    void testCacheManagerCreation() {
        CacheManager cacheManager = cacheConfig.cacheManager(
            serviceOneClient,
            serviceTwoClient,
            scheduledExecutorService
        );

        assertNotNull(cacheManager, "Cache manager should be created");
        assertTrue(cacheManager.getCacheNames().contains("serviceOneCache"), "Service one cache should be created");
        assertTrue(cacheManager.getCacheNames().contains("serviceTwoCache"), "Service two cache should be created");
    }

    @Test
    void testCacheRefreshExecutor() {
        assertNotNull(cacheConfig.cacheRefreshExecutor(), "Cache refresh executor should be created");
    }

    @Test
    void testTaskScheduler() {
        assertNotNull(cacheConfig.taskScheduler(), "Task scheduler should be created");
    }

    @Test
    void testCacheConfiguration() {
        assertNotNull(cacheConfig.getActiveCacheKeys(), "Active cache keys map should be initialized");
    }
}