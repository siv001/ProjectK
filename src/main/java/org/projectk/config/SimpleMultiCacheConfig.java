package org.projectk.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)
public class SimpleMultiCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        
        // Create cache for Service One with a 10-minute expiry and 5-minute refresh
        Cache serviceOneCache = new CaffeineCache("serviceOneCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .refreshAfterWrite(5, TimeUnit.MINUTES)
                        .build());

        // Create cache for Service Two with a 20-minute expiry and 10-minute refresh
        Cache serviceTwoCache = new CaffeineCache("serviceTwoCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(20, TimeUnit.MINUTES)
                        .refreshAfterWrite(10, TimeUnit.MINUTES)
                        .build());

        cacheManager.setCaches(Arrays.asList(serviceOneCache, serviceTwoCache));
        
        // Important: Initialize the cache manager before returning it
        cacheManager.afterPropertiesSet();

        return cacheManager;
    }
}
