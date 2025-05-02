package org.projectk.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Configuration
@EnableCaching
@EnableScheduling
public class CacheConfig {

    // Track cache keys with their TTL information
    private final Map<String, CacheKeyInfo> activeCacheKeys = new ConcurrentHashMap<>();
    private CustomCaffeineCacheManager cacheManager;
    private TaskScheduler taskScheduler;
    
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService cacheRefreshExecutor() {
        return Executors.newScheduledThreadPool(2);
    }
    
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("cache-refresh-scheduler-");
        scheduler.initialize();
        this.taskScheduler = scheduler;
        return scheduler;
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
            try {
                if (cacheManager != null && cacheManager.getCache(keyInfo.getCacheName()) != null) {
                    System.out.println("Refreshing cache entry: " + keyInfo.getCacheName() + ":" + keyInfo.getKey() + " at " + Instant.now());
                    
                    // Force refresh by explicitly removing and then fetching the key
                    cacheManager.getCache(keyInfo.getCacheName()).evict(keyInfo.getKey());
                    cacheManager.getCache(keyInfo.getCacheName()).get(keyInfo.getKey());
                    
                    // Reschedule for next TTL period
                    String cacheKeyStr = keyInfo.getCacheName() + ":" + keyInfo.getKey();
                    CacheKeyInfo updatedInfo = activeCacheKeys.get(cacheKeyStr);
                    if (updatedInfo != null) {
                        // Use the possibly updated TTL value
                        System.out.println("Next refresh scheduled for: " + updatedInfo.getNextRefreshTime() + " (TTL: " + updatedInfo.getTtlSeconds() + " seconds)");
                        scheduleRefresh(updatedInfo);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error refreshing cache entry: " + e.getMessage());
                
                // Reschedule even on error, with default TTL
                String cacheKeyStr = keyInfo.getCacheName() + ":" + keyInfo.getKey();
                if (activeCacheKeys.containsKey(cacheKeyStr)) {
                    keyInfo.setNextRefreshTime(Instant.now().plusSeconds(keyInfo.getTtlSeconds()));
                    System.out.println("Rescheduling after error for: " + keyInfo.getNextRefreshTime());
                    scheduleRefresh(keyInfo);
                }
            }
        }, keyInfo.getNextRefreshTime());
    }
    
    /**
     * Helper class to store cache key information with TTL
     */
    private static class CacheKeyInfo {
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
