package org.projectk.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.mockito.Mockito;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Test-specific configuration that provides mock implementations
 * to avoid conflicts with production beans during testing.
 */
@org.springframework.boot.test.context.TestConfiguration
@Profile("test")
public class TestConfiguration {

    @Bean
    @Primary
    public RestTemplate testRestTemplate() {
        return new RestTemplate();
    }
    
    @Bean
    @Primary
    public CircuitBreakerRegistry testCircuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMillis(1000))
            .slidingWindowSize(5)
            .build();
        return CircuitBreakerRegistry.of(config);
    }
    
    @Bean
    @Primary
    public ServiceOneClient mockServiceOneClient() {
        return Mockito.mock(ServiceOneClient.class);
    }
    
    @Bean
    @Primary
    public ServiceTwoClient mockServiceTwoClient() {
        return Mockito.mock(ServiceTwoClient.class);
    }
    
    @Bean
    @Primary
    public CacheManager testCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        
        // Create a simple configuration for tests
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES);
            
        manager.setCaffeine(caffeine);
        manager.setCacheNames(java.util.Arrays.asList("serviceOneCache", "serviceTwoCache"));
        
        return manager;
    }
}
