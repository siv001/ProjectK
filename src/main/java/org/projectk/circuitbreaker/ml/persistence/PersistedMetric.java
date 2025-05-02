package org.projectk.circuitbreaker.ml.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a persisted circuit breaker metric data point with timestamp information.
 * Used for storing metrics in database and retrieving historical data.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PersistedMetric {
    private String circuitBreakerName;
    private LocalDateTime timestamp;
    private double failureRate;
    private long callVolume;
    private double avgResponseTime;
    private double systemLoad;
}
