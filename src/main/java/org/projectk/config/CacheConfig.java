package org.projectk.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.projectk.service.ServiceThreeClient;
import org.springframework.cache.interceptor.KeyGenerator;
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
    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

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
    public KeyGenerator employeeKeyGenerator() {
        return new EmployeeKeyGenerator();
    }
    
    @Bean
    public CustomCaffeineCacheManager customCaffeineCacheManager() {
        return new CustomCaffeineCacheManager();
    }
    
    @Bean
    @Primary
    public CacheManager cacheManager(ServiceOneClient oneClient,
                                     ServiceTwoClient twoClient,
                                     ServiceThreeClient threeClient,
                                     ScheduledExecutorService cacheRefreshExecutor,
                                     CustomCaffeineCacheManager customCaffeineCacheManager) {
        this.cacheManager = customCaffeineCacheManager;

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
//
//        // Service Three cache with Employee objects: expires 5m after write, uses dynamic TTL-based refresh
//        this.cacheManager.registerCache(
//                "serviceThreeCache",
//                Caffeine.newBuilder()
//                        .expireAfterWrite(300, TimeUnit.SECONDS)
//                        .executor(cacheRefreshExecutor),
//                key -> threeClient.fetchFresh(key)
//        );

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
        // Use a default poll frequency that's 1/3 of the TTL, with a minimum of 10 seconds
        long defaultPollFrequency = Math.max(ttlSeconds / 3, 10);
        trackCacheKey(cacheName, key, ttlSeconds, defaultPollFrequency);
    }
    
    /**
     * Track a cache key with its TTL and poll frequency for scheduled refreshing
     * @param cacheName The name of the cache
     * @param key The cache key
     * @param ttlSeconds The TTL value in seconds from the service response
     * @param pollFrequency The polling frequency in seconds to use when data hasn't changed
     */
    public void trackCacheKey(String cacheName, String key, long ttlSeconds, long pollFrequency) {
        // Minimum TTL of 30 seconds to avoid excessive refreshing
        ttlSeconds = Math.max(ttlSeconds, 60);
        
        // Calculate when the next refresh should happen
        Instant refreshTime = Instant.now().plusSeconds(ttlSeconds);
        
        String cacheKey = cacheName + ":" + key;
        CacheKeyInfo keyInfo = activeCacheKeys.get(cacheKey);
        
        if (keyInfo == null) {
            // First time we're seeing this key
            keyInfo = new CacheKeyInfo(cacheName, key, refreshTime,Instant.now(), ttlSeconds, pollFrequency);
            activeCacheKeys.put(cacheKey, keyInfo);
            logger.info("First tracking of key: {} scheduling refresh at: {} (TTL: {} seconds)", cacheKey, refreshTime, ttlSeconds);
            scheduleRefresh(keyInfo);
        } else {
            // Update existing key info with new TTL
            logger.info("Updating existing key: {}, old refresh at: {}, new refresh at: {}", cacheKey, keyInfo.getNextRefreshTime(), refreshTime);
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
                
                logger.info("Refreshing cache entry asynchronously: {}:{} at {}", cacheName, cachedKey, Instant.now());
                
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
                        // Successfully fetched fresh value, now compare with existing value
                        Object freshValue = freshValueWrapper.get();
                        Object existingValueObj = existingValue != null ? existingValue.get() : null;
                        
                        // Calculate hash for both objects to check if content has changed
                        int freshHash = freshValue != null ? freshValue.hashCode() : 0;
                        int existingHash = existingValueObj != null ? existingValueObj.hashCode() : -1;
                        
                        if (freshHash != existingHash) {
                            // Only update if hash values are different (data has changed)
                            cache.put(cachedKey, freshValue);
                            keyInfo.setLastRefreshTime(Instant.now());
                            
                            // Check if we should update based on original schedule or current time
                            Instant now = Instant.now();
                            Instant originalNext = keyInfo.getOriginalScheduleTime();
                            
                            // If current time is beyond the original next schedule time
                            // use the original schedule's next interval
                            if (now.isAfter(originalNext)) {
                                // Find the next future schedule time that maintains the original pattern
                                while (originalNext.isBefore(now)) {
                                    keyInfo.updateOriginalScheduleTime();
                                    originalNext = keyInfo.getOriginalScheduleTime();
                                }
                                keyInfo.setNextRefreshTime(originalNext);
                                logger.info("Successfully refreshed cache entry with new data: {}:{}, maintaining original schedule at {}", 
                                        cacheName, cachedKey, keyInfo.getNextRefreshTime());
                            } else {
                                // Otherwise use the original schedule time
                                keyInfo.setNextRefreshTime(originalNext);
                                logger.info("Successfully refreshed cache entry with new data: {}:{}, resuming original schedule at {}", 
                                        cacheName, cachedKey, keyInfo.getNextRefreshTime());
                            }
                        } else {
                            // Data didn't change, schedule next check using poll frequency
                            // BUT never exceed the original schedule time
                            Instant pollTime = Instant.now().plusSeconds(keyInfo.getPollFrequency());
                            Instant originalNext = keyInfo.getOriginalScheduleTime();
                            
                            // Use the earlier of poll time or original schedule time
                            if (pollTime.isBefore(originalNext)) {
                                keyInfo.setNextRefreshTime(pollTime);
                                logger.info("Skipping cache update as data hasn't changed: {}:{}, polling again at {}", 
                                        cacheName, cachedKey, keyInfo.getNextRefreshTime());
                            } else {
                                keyInfo.setNextRefreshTime(originalNext);
                                logger.info("Skipping cache update as data hasn't changed: {}:{}, reverting to original schedule at {}", 
                                        cacheName, cachedKey, keyInfo.getNextRefreshTime());
                            }
                        }
                        
                        // Clean up the temporary entry
                        cache.evict(tempKey);
                    } else {
                        // Service returned null, schedule retry using poll frequency
                        // BUT never exceed the original schedule time
                        Instant pollTime = Instant.now().plusSeconds(keyInfo.getPollFrequency());
                        Instant originalNext = keyInfo.getOriginalScheduleTime();
                        
                        // Use the earlier of poll time or original schedule time
                        if (pollTime.isBefore(originalNext)) {
                            keyInfo.setNextRefreshTime(pollTime);
                            logger.info("Fresh value was null for: {}:{}, polling again at {}", 
                                    cacheName, cachedKey, keyInfo.getNextRefreshTime());
                        } else {
                            keyInfo.setNextRefreshTime(originalNext);
                            logger.info("Fresh value was null for: {}:{}, reverting to original schedule at {}", 
                                    cacheName, cachedKey, keyInfo.getNextRefreshTime());
                        }
                        // Clean up the temporary entry
                        cache.evict(tempKey);
                    }
                } catch (Exception serviceException) {
                    // Service call failed, restore the original value if needed
                    logger.error("Service call failed for key: {}:{}, keeping existing cache entry. Error: {}", cacheName, cachedKey, serviceException.getMessage());
                    
                    // If somehow the original value was removed, restore it
                    if (existingValue != null && cache.get(cachedKey) == null) {
                        cache.put(cachedKey, existingValue.get());
                        logger.info("Restored original cache value after service failure");
                    }
                    
                    // Schedule retry using poll frequency for backoff after error
                    // BUT never exceed the original schedule time
                    Instant pollTime = Instant.now().plusSeconds(keyInfo.getPollFrequency());
                    Instant originalNext = keyInfo.getOriginalScheduleTime();
                    
                    // Use the earlier of poll time or original schedule time
                    if (pollTime.isBefore(originalNext)) {
                        keyInfo.setNextRefreshTime(pollTime);
                        logger.info("Service error occurred, polling again at {}", keyInfo.getNextRefreshTime());
                    } else {
                        keyInfo.setNextRefreshTime(originalNext);
                        logger.info("Service error occurred, reverting to original schedule at {}", keyInfo.getNextRefreshTime());
                    }
                }

                // Schedule the next refresh based on the updated nextRefreshTime
                // The nextRefreshTime will have been set in one of the above blocks based on the outcome
                scheduleRefresh(keyInfo);
            }
        } catch (Exception e) {
            logger.error("Error refreshing cache entry: {}", e.getMessage());
            logger.error("Error stack trace:", e); // More detailed logging for troubleshooting

            // Reschedule even on error, with default TTL
            String cacheKeyStr = keyInfo.getCacheName() + ":" + keyInfo.getKey();
            if (activeCacheKeys.containsKey(cacheKeyStr)) {
                keyInfo.setNextRefreshTime(Instant.now().plusSeconds(keyInfo.getTtlSeconds()));
                logger.info("Rescheduling after error for: {}", keyInfo.getNextRefreshTime());
                scheduleRefresh(keyInfo);
            }
        }
    }


    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
