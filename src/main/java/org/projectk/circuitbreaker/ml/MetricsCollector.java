package org.projectk.circuitbreaker.ml;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Collects and maintains a window of service metrics
 */
public class MetricsCollector {
    private final Queue<ServiceMetric> metrics = new ConcurrentLinkedQueue<>();
    private static final int WINDOW_SIZE = 1000;

    /**
     * Records a new metric in the collection window
     *
     * @param metric The metric to record
     */
    public void recordMetric(ServiceMetric metric) {
        metrics.offer(metric);
        if (metrics.size() > WINDOW_SIZE) {
            metrics.poll();
        }
    }

    /**
     * @return A snapshot of the current metrics
     */
    public MetricSnapshot getCurrentMetrics() {
        return new MetricSnapshot(new ArrayList<>(metrics));
    }
    
    /**
     * Creates an empty metrics snapshot for fallback situations
     * 
     * @return An empty metrics snapshot
     */
    public MetricSnapshot createEmptySnapshot() {
        return new MetricSnapshot(new ArrayList<>());
    }
}
