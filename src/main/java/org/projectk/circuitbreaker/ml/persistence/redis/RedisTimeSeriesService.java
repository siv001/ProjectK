package org.projectk.circuitbreaker.ml.persistence.redis;

import lombok.extern.slf4j.Slf4j;
import org.projectk.circuitbreaker.ml.MetricSnapshot;
import org.projectk.circuitbreaker.ml.persistence.MetricsPersistenceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Service that uses Redis TimeSeries for metrics persistence.
 * This is a placeholder implementation that will be replaced with
 * actual Redis functionality when Redis dependencies are available.
 */
@Slf4j
@Service
@Profile({"redis", "embedded-redis"}) // Activate only when Redis is enabled
@ConditionalOnProperty(name = "metrics.redis.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate") // Only activate if Redis is on classpath
public class RedisTimeSeriesService implements MetricsPersistenceProvider {

    /**
     * Stores a metrics snapshot for a specific circuit breaker.
     * This is a stub implementation that just logs the operation.
     *
     * @param snapshot The metrics snapshot to store
     * @param circuitBreakerName The name of the circuit breaker
     */
    @Override
    public void storeMetrics(MetricSnapshot snapshot, String circuitBreakerName) {
        log.debug("Redis persistence is not active - would store {} metrics for {}", 
                 snapshot != null && snapshot.getMetrics() != null ? snapshot.getMetrics().size() : 0, 
                 circuitBreakerName);
    }

    /**
     * Loads historical metrics for a circuit breaker.
     * This is a stub implementation that returns empty data.
     *
     * @param circuitBreakerName The name of the circuit breaker
     * @param lookbackPeriod How far back to retrieve metrics
     * @return Empty list since this is a stub implementation
     */
    @Override
    public List<MetricSnapshot> loadHistoricalMetrics(String circuitBreakerName, Duration lookbackPeriod) {
        log.debug("Redis persistence is not active - would load metrics for {} with lookback {}", 
                 circuitBreakerName, lookbackPeriod);
        return Collections.emptyList();
    }

    /**
     * Shutdown hook implementation.
     * Nothing to do in this stub implementation.
     */
    @Override
    public void shutdown() {
        log.debug("Redis persistence is not active - shutdown called");
    }
}
