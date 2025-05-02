package org.projectk.cache;

import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * A simple component that directly calls service methods to warm up caches on application startup.
 * Since the service methods are annotated with @Cacheable, calling them will populate the cache.
 */
@Component
public class SimpleCacheWarmer {
    private static final Logger logger = LoggerFactory.getLogger(SimpleCacheWarmer.class);
    
    private final ServiceOneClient serviceOneClient;
    private final ServiceTwoClient serviceTwoClient;
    
    public SimpleCacheWarmer(ServiceOneClient serviceOneClient, ServiceTwoClient serviceTwoClient) {
        this.serviceOneClient = serviceOneClient;
        this.serviceTwoClient = serviceTwoClient;
    }
    
    @EventListener(ApplicationStartedEvent.class)
    public void warmCaches() {
        logger.info("Starting direct cache warming");
        
        try {
            // Warm ServiceOne cache
            List<String> serviceOneKeys = Arrays.asList("common-id-1", "common-id-2", "frequent-access-id");
            logger.info("Warming ServiceOne cache with {} keys", serviceOneKeys.size());
            for (String key : serviceOneKeys) {
                serviceOneClient.fetchData(key);
                logger.debug("Warmed ServiceOne cache with key: {}", key);
            }
            
            // Warm ServiceTwo cache
            List<String> serviceTwoKeys = Arrays.asList("popular-item", "system-config");
            logger.info("Warming ServiceTwo cache with {} keys", serviceTwoKeys.size());
            for (String key : serviceTwoKeys) {
                serviceTwoClient.fetchData(key);
                logger.debug("Warmed ServiceTwo cache with key: {}", key);
            }
            
            logger.info("Direct cache warming completed successfully");
        } catch (Exception e) {
            logger.error("Error during cache warming", e);
            // We don't want to fail application startup if cache warming fails
        }
    }
}
