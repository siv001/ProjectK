package org.projectk.circuitbreaker.ml;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a snapshot of service metrics over a time window
 */
@Data
public class MetricSnapshot {
    private final List<ServiceMetric> metrics;

    /**
     * @return The 95th percentile latency from the metric sample
     */
    public double getP95Latency() {
        return calculateP95Latency();
    }

    /**
     * @return The error rate from the metric sample
     */
    public double getErrorRate() {
        return calculateErrorRate();
    }

    /**
     * @return The success rate (1 - error rate)
     */
    public double getSuccessRate() {
        return 1.0 - getErrorRate();
    }

    /**
     * @return The average concurrent calls across all metrics
     */
    public double getConcurrency() {
        return metrics.stream()
            .mapToInt(ServiceMetric::getConcurrentCalls)
            .average()
            .orElse(0.0);
    }

    /**
     * @return The average system load across all metrics
     */
    public double getSystemLoad() {
        return metrics.stream()
            .mapToDouble(ServiceMetric::getSystemLoad)
            .average()
            .orElse(0.0);
    }

    /**
     * @return The normalized time of day (hour/24) as a feature for ML
     */
    public double getTimeOfDay() {
        return LocalDateTime.now().getHour() / 24.0;
    }

    /**
     * Calculates the 95th percentile latency from the metric sample
     */
    private double calculateP95Latency() {
        List<Long> latencies = metrics.stream()
            .map(ServiceMetric::getLatency)
            .sorted()
            .collect(Collectors.toList());

        if (latencies.isEmpty()) {
            return 0;
        }

        int index = (int) Math.ceil(0.95 * latencies.size()) - 1;
        return latencies.get(Math.max(0, index));
    }

    /**
     * Calculates the error rate as failures / total
     */
    private double calculateErrorRate() {
        if (metrics.isEmpty()) {
            return 0;
        }
        
        long failures = metrics.stream()
            .filter(m -> !m.isSuccess())
            .count();
        return (double) failures / metrics.size();
    }
}
