package org.projectk.circuitbreaker.ml.persistence;

import org.springframework.context.annotation.Profile;

import lombok.extern.slf4j.Slf4j;
import org.projectk.circuitbreaker.ml.MetricSnapshot;
import org.projectk.circuitbreaker.ml.ServiceMetric;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service responsible for persisting metrics data to ensure history is maintained
 * across application restarts. Provides both in-memory caching and database persistence.
 */
@Slf4j
@Service
@Profile({"default", "metrics"}) // Active by default or with metrics profile
public class MetricsPersistenceService implements MetricsPersistenceProvider {

    private final ConcurrentLinkedQueue<PersistedMetric> metricsCache = new ConcurrentLinkedQueue<>();
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Value("${metrics.persistence.enabled:true}")
    private boolean persistenceEnabled;
    
    @Value("${metrics.persistence.batch-size:100}")
    private int batchSize;
    
    @Value("${metrics.persistence.retention-days:30}")
    private int retentionDays;
    
    /**
     * Initialize the metrics persistence service
     */
    @PostConstruct
    public void init() {
        if (persistenceEnabled) {
            createMetricsTableIfNotExists();
            purgeOldMetrics();
            log.info("Metrics persistence initialized with retention period of {} days", retentionDays);
        } else {
            log.info("Metrics persistence is disabled");
        }
    }
    
    /**
     * Store a new metric snapshot
     * 
     * @param snapshot The metrics snapshot to store
     * @param circuitBreakerName The name of the circuit breaker that generated these metrics
     */
    public void storeMetrics(MetricSnapshot snapshot, String circuitBreakerName) {
        if (!persistenceEnabled) return;
        
        PersistedMetric persistedMetric = new PersistedMetric(
            circuitBreakerName,
            LocalDateTime.now(),
            snapshot.getErrorRate(),
            snapshot.getMetrics().size(),  // Use size of metrics list as call volume
            snapshot.getP95Latency(),     // Use p95 latency as response time metric
            snapshot.getSystemLoad()
        );
        
        // Add to in-memory cache
        metricsCache.add(persistedMetric);
        
        log.debug("Added metrics to persistence cache for {}: failure rate={}, call volume={}", 
                 circuitBreakerName, snapshot.getErrorRate(), snapshot.getMetrics().size());
    }
    
    /**
     * Load historical metrics for a specific circuit breaker within a lookback period
     *
     * @param circuitBreakerName The name of the circuit breaker
     * @param lookbackPeriod How far back to retrieve metrics
     * @return List of metric snapshots ordered by timestamp
     */
    public List<MetricSnapshot> loadHistoricalMetrics(String circuitBreakerName, Duration lookbackPeriod) {
        if (!persistenceEnabled) {
            return List.of(); // Return empty list if persistence is disabled
        }
        
        LocalDateTime cutoffTime = LocalDateTime.now().minus(lookbackPeriod);
        
        // Query database for historical metrics
        String sql = "SELECT * FROM circuit_breaker_metrics " +
                   "WHERE circuit_breaker_name = ? AND timestamp > ? " +
                   "ORDER BY timestamp ASC";
        
        return jdbcTemplate.query(sql, 
            (rs, rowNum) -> {
                // Convert database row to MetricSnapshot
                // Create a ServiceMetric with data from the database
                ServiceMetric metric = new ServiceMetric(
                    System.currentTimeMillis(), // current timestamp
                    rs.getLong("avg_response_time"),
                    rs.getDouble("failure_rate") < 0.5, // Approximate success flag
                    1, // Default concurrency value 
                    rs.getDouble("system_load")
                );
                
                // Return a new MetricSnapshot with this single metric
                // Note: This is an approximation as we're reconstituting from summary data
                return new MetricSnapshot(List.of(metric));
            },
            circuitBreakerName, cutoffTime);
    }
    
    /**
     * Periodically flush the in-memory cache to the database
     */
    @Scheduled(fixedRateString = "${metrics.persistence.flush-interval-ms:60000}")
    public void flushMetricsToDatabase() {
        if (!persistenceEnabled || metricsCache.isEmpty()) return;
        
        int count = 0;
        while (!metricsCache.isEmpty() && count < batchSize) {
            PersistedMetric metric = metricsCache.poll();
            if (metric != null) {
                saveMetricToDatabase(metric);
                count++;
            }
        }
        
        if (count > 0) {
            log.info("Flushed {} metrics to database", count);
        }
    }
    
    /**
     * Cleanup hook to ensure remaining metrics are persisted on shutdown
     */
    public void shutdown() {
        if (!persistenceEnabled) return;
        
        log.info("Flushing remaining {} metrics before shutdown", metricsCache.size());
        metricsCache.forEach(this::saveMetricToDatabase);
    }
    
    // Private helper methods
    
    private void createMetricsTableIfNotExists() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS circuit_breaker_metrics (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  circuit_breaker_name VARCHAR(255) NOT NULL," +
            "  timestamp TIMESTAMP NOT NULL," +
            "  failure_rate DOUBLE NOT NULL," +
            "  call_volume BIGINT NOT NULL," +
            "  avg_response_time DOUBLE NOT NULL," +
            "  system_load DOUBLE NOT NULL" +
            ");");
        
        // Create index in a separate statement for H2 compatibility
        try {
            jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_cb_name_timestamp ON circuit_breaker_metrics " +
                "(circuit_breaker_name, timestamp);");
        } catch (Exception e) {
            log.warn("Could not create index on circuit_breaker_metrics table: {}", e.getMessage());
        }
    }
    
    private void saveMetricToDatabase(PersistedMetric metric) {
        jdbcTemplate.update(
            "INSERT INTO circuit_breaker_metrics " +
            "(circuit_breaker_name, timestamp, failure_rate, call_volume, avg_response_time, system_load) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            metric.getCircuitBreakerName(),
            metric.getTimestamp(),
            metric.getFailureRate(),
            metric.getCallVolume(),
            metric.getAvgResponseTime(),
            metric.getSystemLoad()
        );
    }
    
    private void purgeOldMetrics() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        int deletedCount = jdbcTemplate.update(
            "DELETE FROM circuit_breaker_metrics WHERE timestamp < ?", 
            cutoffDate
        );
        log.info("Purged {} metrics older than {} days", deletedCount, retentionDays);
    }
}
