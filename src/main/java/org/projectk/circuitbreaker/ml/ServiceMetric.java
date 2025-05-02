package org.projectk.circuitbreaker.ml;

import lombok.Data;

/**
 * Represents a single metric data point for a service call
 */
@Data
public class ServiceMetric {
    private final long timestamp;
    private final long latency;
    private final boolean success;
    private final int concurrentCalls;
    private final double systemLoad;
}
