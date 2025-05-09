package org.projectk.config;

import org.mockito.Mockito;
import org.projectk.cache.SimpleCacheWarmer;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.projectk.service.ServiceThreeClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

/**
 * Test configuration that provides mock beans to avoid real service calls during tests
 */
@Configuration
public class TestCacheConfig {

    /**
     * Create a mock ServiceOneClient to use in tests
     */
    @Bean
    @Primary
    public ServiceOneClient serviceOneClient() {
        return Mockito.mock(ServiceOneClient.class);
    }

    /**
     * Create a mock ServiceTwoClient to use in tests
     */
    @Bean
    @Primary
    public ServiceTwoClient serviceTwoClient() {
        return Mockito.mock(ServiceTwoClient.class);
    }
    
    /**
     * Create a mock ServiceThreeClient to use in tests
     */
    @Bean
    @Primary
    public ServiceThreeClient serviceThreeClient() {
        return Mockito.mock(ServiceThreeClient.class);
    }
    

    /**
     * Create a TaskScheduler for testing
     */
    @Bean
    @Primary
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("test-task-scheduler-");
        scheduler.initialize();
        return scheduler;
    }

    /**
     * Create a ScheduledExecutorService for testing
     */
    @Bean
    @Primary
    public ScheduledExecutorService cacheRefreshExecutor() {
        return Executors.newScheduledThreadPool(1);
    }

    /**
     * Create a mock SimpleCacheWarmer for testing
     */
    @Bean
    @Primary
    public SimpleCacheWarmer simpleCacheWarmer(
            ServiceOneClient serviceOneClient,
            ServiceTwoClient serviceTwoClient) {
        return new SimpleCacheWarmer(serviceOneClient, serviceTwoClient);
    }
}
