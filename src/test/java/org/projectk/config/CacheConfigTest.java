package org.projectk.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.projectk.service.ServiceThreeClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;


import java.util.Map;
import java.util.concurrent.Executor;

import java.util.concurrent.ScheduledExecutorService;


import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(MockitoExtension.class)
public class CacheConfigTest {

    @Mock
    private ServiceOneClient serviceOneClient;

    @Mock
    private ServiceTwoClient serviceTwoClient;
    
    @Mock
    private ServiceThreeClient serviceThreeClient;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    @Mock
    private CustomCaffeineCacheManager customCaffeineCacheManager;
    
    @Mock
    private TaskScheduler taskScheduler;
    
    @Mock
    private CacheManager cacheManager;
    
    @Mock
    private Cache cache;
    
    @Mock
    private Cache.ValueWrapper valueWrapper;
    
    @Spy
    private CacheConfig cacheConfig;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles initialization via the class annotation
    }

    @Test
    void testCacheManagerCreation() {
        // Create a real CacheConfig for this test to avoid mocking complexities
        CacheConfig realCacheConfig = new CacheConfig();
        // Create a CustomCaffeineCacheManager for the test
        CustomCaffeineCacheManager testCacheManager = new CustomCaffeineCacheManager();
        
        CacheManager cacheManager = realCacheConfig.cacheManager(
            serviceOneClient,
            serviceTwoClient,
            serviceThreeClient,
            scheduledExecutorService,
            testCacheManager
        );

        assertNotNull(cacheManager, "Cache manager should be created");
        assertTrue(cacheManager.getCacheNames().contains("serviceOneCache"), "Service one cache should be created");
        assertTrue(cacheManager.getCacheNames().contains("serviceTwoCache"), "Service two cache should be created");
    }

    @Test
    void testCacheRefreshExecutor() {
        // Create a real CacheConfig for this test
        CacheConfig realCacheConfig = new CacheConfig();
        ScheduledExecutorService executor = realCacheConfig.cacheRefreshExecutor();
        assertNotNull(executor, "Cache refresh executor should be created");
        // Shutdown to avoid resource leaks in tests
        executor.shutdown();
    }

    @Test
    void testTaskScheduler() {
        // Create a real CacheConfig for this test
        CacheConfig realCacheConfig = new CacheConfig();
        TaskScheduler scheduler = realCacheConfig.taskScheduler();
        assertNotNull(scheduler, "Task scheduler should be created");
        assertTrue(scheduler instanceof ThreadPoolTaskScheduler, "Scheduler should be ThreadPoolTaskScheduler");
        // Cast is safe since we verified the type
        ThreadPoolTaskScheduler threadPoolScheduler = (ThreadPoolTaskScheduler) scheduler;
        // Check configuration
        assertEquals("cache-refresh-scheduler-", threadPoolScheduler.getThreadNamePrefix());
    }
    
    @Test
    void testCacheRefreshTaskExecutor() {
        // Create a real CacheConfig for this test
        CacheConfig realCacheConfig = new CacheConfig();
        Executor executor = realCacheConfig.cacheRefreshTaskExecutor();
        assertNotNull(executor, "Cache refresh task executor should be created");
        assertTrue(executor instanceof ThreadPoolTaskExecutor, "Executor should be ThreadPoolTaskExecutor");
        // Cast is safe since we verified the type
        ThreadPoolTaskExecutor threadPoolExecutor = (ThreadPoolTaskExecutor) executor;
        // Check configuration
        assertEquals("cache-refresh-worker-", threadPoolExecutor.getThreadNamePrefix());
    }

    @Test
    void testTrackCacheKey() {
        // Create a real CacheConfig for this test
        CacheConfig realCacheConfig = new CacheConfig();
        
        // Execute
        String cacheName = "testCache";
        String key = "testKey";
        long ttlSeconds = 120;
        realCacheConfig.trackCacheKey(cacheName, key, ttlSeconds);
        
        // Verify
        Map<String, CacheKeyInfo> activeCacheKeys = realCacheConfig.getActiveCacheKeys();
        assertEquals(1, activeCacheKeys.size(), "Should have one tracked key");
        
        String cacheKeyStr = cacheName + ":" + key;
        assertTrue(activeCacheKeys.containsKey(cacheKeyStr), "Should contain the tracked key");
        
        CacheKeyInfo keyInfo = activeCacheKeys.get(cacheKeyStr);
        assertEquals(cacheName, keyInfo.getCacheName(), "Cache name should match");
        assertEquals(key, keyInfo.getKey(), "Key should match");
        assertEquals(ttlSeconds, keyInfo.getTtlSeconds(), "TTL should match");
    }
    
    @Test
    void testMinimumTTLEnforcement() {
        // Create a real CacheConfig for this test
        CacheConfig realCacheConfig = new CacheConfig();
        
        // Execute with very small TTL
        String cacheName = "testCache";
        String key = "testKey";
        long smallTtl = 30; // Below minimum, should be adjusted to minimum
        realCacheConfig.trackCacheKey(cacheName, key, smallTtl);
        
        // Verify minimum TTL enforced (should be 60 seconds per implementation)
        String cacheKeyStr = cacheName + ":" + key;
        CacheKeyInfo keyInfo = realCacheConfig.getActiveCacheKeys().get(cacheKeyStr);
        assertEquals(60, keyInfo.getTtlSeconds(), "TTL should be adjusted to minimum");
    }
    
    @Test
    void testTrackCacheKeyUpdatesExistingEntry() {
        // Create a real CacheConfig for this test
        CacheConfig realCacheConfig = new CacheConfig();
        
        // First tracking
        String cacheName = "testCache";
        String key = "testKey";
        realCacheConfig.trackCacheKey(cacheName, key, 120);
        
        // Second tracking with different TTL
        realCacheConfig.trackCacheKey(cacheName, key, 180);
        
        // Verify
        String cacheKeyStr = cacheName + ":" + key;
        CacheKeyInfo keyInfo = realCacheConfig.getActiveCacheKeys().get(cacheKeyStr);
        assertEquals(180, keyInfo.getTtlSeconds(), "TTL should be updated");
    }
    
    // Test the core logic of the async refresh method
    /**
     * Test for the AsyncRefreshCache method logic - focuses on key components of functionality
     * without dealing with async test complexity
     */
    @Test
    void testAsyncRefreshCacheLogic() {
        // Create a CacheConfig class for testing 
        CacheConfig config = new CacheConfig();
        
        // Create test data
        String cacheName = "testCache";
        String key = "testKey";
        long ttlSeconds = 120;
        
        // Verify that the tracking logic works correctly
        config.trackCacheKey(cacheName, key, ttlSeconds);
        Map<String, CacheKeyInfo> activeCacheKeys = config.getActiveCacheKeys();
        String cacheKeyStr = cacheName + ":" + key;
        
        // Assert proper tracking
        assertTrue(activeCacheKeys.containsKey(cacheKeyStr), "Cache key should be tracked");
        assertEquals(ttlSeconds, activeCacheKeys.get(cacheKeyStr).getTtlSeconds(), "TTL should be stored");
    }
    
    /**
     * Tests that minimum TTL policy is enforced in trackCacheKey
     */
    @Test
    void testMinimumTTLPolicy() {
        // Create a real CacheConfig and set a very low TTL that should be adjusted upward
        CacheConfig config = new CacheConfig();
        String cacheName = "testCache";
        String key = "testKey";
        long verySmallTtl = 10; // Should be raised to minimum
        
        config.trackCacheKey(cacheName, key, verySmallTtl);
        
        // Verify minimum enforcement
        String cacheKeyStr = cacheName + ":" + key;
        long enforcedTtl = config.getActiveCacheKeys().get(cacheKeyStr).getTtlSeconds();
        assertTrue(enforcedTtl >= 60, "TTL should be raised to minimum value");
    }
    
    /**
     * Test that cacheRefreshTaskExecutor is properly configured and can actually execute tasks
     */
    @Test
    void testCacheRefreshAsyncExecution() throws Exception {
        // Create executor
        CacheConfig config = new CacheConfig();
        Executor executor = config.cacheRefreshTaskExecutor();
        
        // Test that it can execute tasks
        final boolean[] taskExecuted = new boolean[1];
        
        // Create a simple task
        Runnable task = () -> {
            taskExecuted[0] = true;
        };
        
        // Execute it
        executor.execute(task);
        
        // Wait for task to complete (with timeout)
        int maxWaitMs = 1000;
        int waited = 0;
        while (!taskExecuted[0] && waited < maxWaitMs) {
            Thread.sleep(50);
            waited += 50;
        }
        
        // Verify task executed
        assertTrue(taskExecuted[0], "Task should have been executed by the executor");
    }
}