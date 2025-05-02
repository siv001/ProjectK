package org.projectk.circuitbreaker.ml.persistence.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Configuration for embedded RedisTimeSeries.
 * This is a stub implementation that will be activated only when explicitly configured.
 * The actual implementation would require Testcontainers to be present on the classpath.
 */
@Slf4j
@Configuration
@Profile("embedded-redis") // Only activate with this specific profile
@ConditionalOnClass(name = "org.testcontainers.containers.GenericContainer") // Only load if testcontainers is available
@ConditionalOnProperty(name = "metrics.redis.embedded.enabled", havingValue = "true", matchIfMissing = false)
public class EmbeddedRedisConfig {
    @PostConstruct
    public void startRedis() {
        log.info("EmbeddedRedisConfig is a stub implementation and will not start any container");
        log.info("Add org.testcontainers:testcontainers and the Redis image to your dependencies to enable this feature");
    }

    @PreDestroy
    public void stopRedis() {
        log.info("No embedded Redis container is running - nothing to stop");
    }
}
