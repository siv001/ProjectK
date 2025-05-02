package org.projectk.circuitbreaker.ml.persistence;

import org.projectk.circuitbreaker.ml.MetricSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Interface for metrics persistence implementations.
 * This allows different storage backends to be used interchangeably.
 */
public interface MetricsPersistenceProvider {
    
    /**
     * Store a metrics snapshot for a specific circuit breaker
     * 
     * @param snapshot The metrics snapshot to store
     * @param circuitBreakerName The name of the circuit breaker
     */
    void storeMetrics(MetricSnapshot snapshot, String circuitBreakerName);
    
    /**
     * Load historical metrics for a circuit breaker within a lookback period
     * 
     * @param circuitBreakerName The name of the circuit breaker
     * @param lookbackPeriod How far back to retrieve metrics
     * @return List of metric snapshots ordered by timestamp
     */
    List<MetricSnapshot> loadHistoricalMetrics(String circuitBreakerName, Duration lookbackPeriod);
    
    /**
     * Retrieve time series data points for ML model training
     * 
     * @param serviceName The service name to retrieve data for
     * @param startTime The start time of the time range
     * @param endTime The end time of the time range
     * @return List of time series data points ordered by timestamp
     */
    default List<TimeSeriesDataPoint> getTimeSeriesData(String serviceName, Instant startTime, Instant endTime) {
        // Default implementation converts MetricSnapshot to TimeSeriesDataPoint
        List<MetricSnapshot> snapshots = loadHistoricalMetrics(
                serviceName, 
                Duration.between(startTime, endTime));
        
        return snapshots.stream()
                .map(snapshot -> TimeSeriesDataPoint.fromMetricSnapshot(snapshot, serviceName))
                .toList();
    }
    
    /**
     * Perform any shutdown operations to ensure data is properly saved
     */
    void shutdown();
}
