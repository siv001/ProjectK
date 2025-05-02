package org.projectk.circuitbreaker.ml.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Represents a single data point in a time series for ML model training and inference.
 * Contains metrics relevant for circuit breaker decision making.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesDataPoint {
    private String serviceName;
    private Instant timestamp;
    
    // Key metrics for ML model
    private double errorRate;
    private double latency;
    private double systemLoad;
    private long callVolume;
    
    // Additional context for analysis
    private String endpoint;
    private int statusCode;
    private boolean circuitOpen;
    
    /**
     * Creates a TimeSeriesDataPoint from a MetricSnapshot for backward compatibility
     * 
     * @param snapshot The source MetricSnapshot
     * @param serviceName The name of the service this snapshot is for
     * @return A new TimeSeriesDataPoint with data from the snapshot
     */
    public static TimeSeriesDataPoint fromMetricSnapshot(org.projectk.circuitbreaker.ml.MetricSnapshot snapshot, String serviceName) {
        return TimeSeriesDataPoint.builder()
                .serviceName(serviceName)
                .timestamp(Instant.now()) // Use current time since MetricSnapshot doesn't have a timestamp
                .errorRate(snapshot.getErrorRate())
                .latency(snapshot.getP95Latency())
                .systemLoad(snapshot.getSystemLoad())
                .callVolume(snapshot.getMetrics().size())
                .circuitOpen(false) // Default to false since MetricSnapshot doesn't track circuit state
                .build();
    }
}
