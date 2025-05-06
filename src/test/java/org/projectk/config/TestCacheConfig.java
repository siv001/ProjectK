package org.projectk.config;

import org.mockito.Mockito;
import org.projectk.cache.SimpleCacheWarmer;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class TestCacheConfig {

    @Bean
    public ServiceOneClient serviceOneClient() {
        return Mockito.mock(ServiceOneClient.class);
    }

    @Bean
    public ServiceTwoClient serviceTwoClient() {
        return Mockito.mock(ServiceTwoClient.class);
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("test-task-scheduler-");
        return scheduler;
    }

    @Bean
    public SimpleCacheWarmer simpleCacheWarmer() {
        return Mockito.mock(SimpleCacheWarmer.class);
    }
}
