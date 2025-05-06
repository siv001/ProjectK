package org.projectk.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Configuration
@EnableCaching
@EnableScheduling
@EnableAsync
public class CacheConfig {

    // Track cache keys with their TTL information
    private final Map<String, CacheKeyInfo> activeCacheKeys = new ConcurrentHashMap<>();

    public Map<String, CacheKeyInfo> getActiveCacheKeys() {
        return activeCacheKeys;
    }

    private CustomCaffeineCacheManager cacheManager;
    private TaskScheduler taskScheduler;
    
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService cacheRefreshExecutor() {
        return Executors.newScheduledThreadPool(2);
    }
    
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3); // Increased pool size for better parallel processing
        scheduler.setThreadNamePrefix("cache-refresh-scheduler-");
        scheduler.initialize();
        this.taskScheduler = scheduler;
        return scheduler;
    }
    
    @Bean
    public Executor cacheRefreshTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("cache-refresh-worker-");
        executor.initialize();
        return executor;
    }

    @Bean
    public CacheManager cacheManager(ServiceOneClient oneClient,
                                     ServiceTwoClient twoClient,
                                     ScheduledExecutorService cacheRefreshExecutor) {
        this.cacheManager = new CustomCaffeineCacheManager();

        // Service One cache: expires 10m after write, uses dynamic TTL-based refresh
        this.cacheManager.registerCache(
                "serviceOneCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(300, TimeUnit.SECONDS)
                        .executor(cacheRefreshExecutor),
                key -> oneClient.fetchFresh((String) key)
        );

        // Service Two cache: expires 20m after write, uses dynamic TTL-based refresh
        this.cacheManager.registerCache(
                "serviceTwoCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(300, TimeUnit.SECONDS)
                        .executor(cacheRefreshExecutor),
                key -> twoClient.fetchFresh((String) key)
        );

        return this.cacheManager;
    }
    
    public CacheManager getCacheManager() {
        return this.cacheManager;
    }

    public ScheduledExecutorService getCacheRefreshExecutor() {
        return cacheRefreshExecutor();
    }

    public TaskScheduler getTaskScheduler() {
        return taskScheduler();
    }
    
    /**
     * Track a cache key with its TTL for scheduled refreshing
     * @param cacheName The name of the cache
     * @param key The cache key
     * @param ttlSeconds The TTL value in seconds from the service response
     */
    public void trackCacheKey(String cacheName, String key, long ttlSeconds) {
        // Minimum TTL of 30 seconds to avoid excessive refreshing
        ttlSeconds = Math.max(ttlSeconds, 60);
        
        // Calculate when the next refresh should happen
        Instant refreshTime = Instant.now().plusSeconds(ttlSeconds);
        
        String cacheKey = cacheName + ":" + key;
        CacheKeyInfo keyInfo = activeCacheKeys.get(cacheKey);
        
        if (keyInfo == null) {
            // First time we're seeing this key
            keyInfo = new CacheKeyInfo(cacheName, key, refreshTime, ttlSeconds);
            activeCacheKeys.put(cacheKey, keyInfo);
            System.out.println("First tracking of key: " + cacheKey + ", scheduling refresh at: " + refreshTime + " (TTL: " + ttlSeconds + " seconds)");
            scheduleRefresh(keyInfo);
        } else {
            // Update existing key info with new TTL
            System.out.println("Updating existing key: " + cacheKey + ", old refresh at: " + keyInfo.getNextRefreshTime() + ", new refresh at: " + refreshTime);
            keyInfo.setNextRefreshTime(refreshTime);
            keyInfo.setTtlSeconds(ttlSeconds);
            // We'll let the existing scheduled task finish - no need to reschedule now
            // The existing schedule will pick up the new TTL when it runs
            // This avoids having multiple refresh tasks for the same key
        }
    }
    
    private void scheduleRefresh(CacheKeyInfo keyInfo) {
        if (taskScheduler == null) return;
        
        taskScheduler.schedule(() -> {
            // The scheduling happens here, but the actual refresh is executed asynchronously
            refreshCacheAsync(keyInfo);
        }, keyInfo.getNextRefreshTime());
    }
    
    /**
     * Asynchronously refreshes a cache entry
     * @param keyInfo The cache key information
     */
    @Async("cacheRefreshTaskExecutor")
    public void refreshCacheAsync(CacheKeyInfo keyInfo) {
        try {
            if (cacheManager != null && cacheManager.getCache(keyInfo.getCacheName()) != null) {
                String cacheName = keyInfo.getCacheName();
                Object cachedKey = keyInfo.getKey();
                Cache cache = cacheManager.getCache(cacheName);
                
                System.out.println("Refreshing cache entry asynchronously: " + cacheName + ":" + cachedKey + " at " + Instant.now());
                
                // Store the existing value before attempting refresh
                Cache.ValueWrapper existingValue = cache.get(cachedKey);
                
                try {
                    // Create a new temporary cache key for the fresh fetch
                    // This allows us to invoke the loader without affecting the original entry
                    String tempKey = "temp_refresh_" + System.currentTimeMillis() + "_" + cachedKey;
                    
                    // Attempt to load the fresh value using a different key
                    // This will invoke the loader function via the cache's normal mechanisms
                    Cache.ValueWrapper freshValueWrapper = cache.get(tempKey);
                    
                    if (freshValueWrapper != null && freshValueWrapper.get() != null) {
                        // Successfully fetched fresh value, now update the real cache entry
                        Object freshValue = freshValueWrapper.get();
                        cache.put(cachedKey, freshValue);
                        System.out.println("Successfully refreshed cache entry: " + cacheName + ":" + cachedKey);
                        
                        // Clean up the temporary entry
                        cache.evict(tempKey);
                    } else {
                        System.out.println("Fresh value was null for: " + cacheName + ":" + cachedKey + ", keeping existing cache entry");
                        // Clean up the temporary entry
                        cache.evict(tempKey);
                    }
                } catch (Exception serviceException) {
                    // Service call failed, restore the original value if needed
                    System.err.println("Service call failed for key: " + cacheName + ":" + cachedKey + ", keeping existing cache entry. Error: " + serviceException.getMessage());
                    
                    // If somehow the original value was removed, restore it
                    if (existingValue != null && cache.get(cachedKey) == null) {
                        cache.put(cachedKey, existingValue.get());
                        System.out.println("Restored original cache value after service failure");
                    }
                }
                
                // Reschedule for next TTL period
                String cacheKeyStr = cacheName + ":" + cachedKey;
                CacheKeyInfo updatedInfo = activeCacheKeys.get(cacheKeyStr);
                if (updatedInfo != null) {
                    // Use the possibly updated TTL value
                    System.out.println("Next refresh scheduled for: " + updatedInfo.getNextRefreshTime() + " (TTL: " + updatedInfo.getTtlSeconds() + " seconds)");
                    scheduleRefresh(updatedInfo);
                }
            }
        } catch (Exception e) {
            System.err.println("Error refreshing cache entry: " + e.getMessage());
            e.printStackTrace(); // More detailed logging for troubleshooting
            
            // Reschedule even on error, with default TTL
            String cacheKeyStr = keyInfo.getCacheName() + ":" + keyInfo.getKey();
            if (activeCacheKeys.containsKey(cacheKeyStr)) {
                keyInfo.setNextRefreshTime(Instant.now().plusSeconds(keyInfo.getTtlSeconds()));
                System.out.println("Rescheduling after error for: " + keyInfo.getNextRefreshTime());
                scheduleRefresh(keyInfo);
            }
        }
    }
    
    /**
     * Helper class to store cache key information with TTL
     */
    static class CacheKeyInfo {
        private final String cacheName;
        private final String key;
        private Instant nextRefreshTime;
        private long ttlSeconds;
        
        public CacheKeyInfo(String cacheName, String key, Instant nextRefreshTime, long ttlSeconds) {
            this.cacheName = cacheName;
            this.key = key;
            this.nextRefreshTime = nextRefreshTime;
            this.ttlSeconds = ttlSeconds;
        }
        
        public String getCacheName() {
            return cacheName;
        }
        
        public String getKey() {
            return key;
        }
        
        public Instant getNextRefreshTime() {
            return nextRefreshTime;
        }
        
        public void setNextRefreshTime(Instant nextRefreshTime) {
            this.nextRefreshTime = nextRefreshTime;
        }
        
        public long getTtlSeconds() {
            return ttlSeconds;
        }
        
        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
