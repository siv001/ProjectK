package org.projectk.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(ServiceOneClient oneClient,
                                     ServiceTwoClient twoClient) {
        CustomCaffeineCacheManager mgr = new CustomCaffeineCacheManager();

        // Service One cache: expires 10m after write, refreshes 5m after write
        mgr.registerCache(
                "serviceOneCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .refreshAfterWrite(5, TimeUnit.MINUTES),
                key -> oneClient.fetchFresh((String) key)
        );

        // Service Two cache: expires 20m after write, refreshes 10m after write
        mgr.registerCache(
                "serviceTwoCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(20, TimeUnit.MINUTES)
                        .refreshAfterWrite(10, TimeUnit.MINUTES),
                key -> twoClient.fetchFresh((String) key)
        );

        return mgr;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
