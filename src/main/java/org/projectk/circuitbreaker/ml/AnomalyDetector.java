package org.projectk.circuitbreaker.ml;

/**
 * Detects anomalies in service metrics that might indicate
 * unusual conditions requiring different circuit breaker behavior
 */
public class AnomalyDetector {
    private static final double ANOMALY_THRESHOLD = -0.5;

    /**
     * Determines if the current metrics represent an anomalous state
     *
     * @param metrics Current metrics snapshot
     * @return True if metrics indicate an anomaly
     */
    public boolean isAnomaly(MetricSnapshot metrics) {
        double[] features = extractFeatures(metrics);
        return calculateAnomalyScore(features) < ANOMALY_THRESHOLD;
    }

    /**
     * Extracts relevant features for anomaly detection
     *
     * @param metrics Current metrics snapshot
     * @return Array of features for anomaly detection
     */
    private double[] extractFeatures(MetricSnapshot metrics) {
        return new double[] {
            metrics.getP95Latency(),
            metrics.getErrorRate(),
            metrics.getConcurrency(),
            metrics.getSystemLoad()
        };
    }

    /**
     * Calculates an anomaly score based on the extracted features
     *
     * @param features Array of feature values
     * @return Anomaly score (lower values indicate higher likelihood of anomaly)
     */
    private double calculateAnomalyScore(double[] features) {
        // Simple implementation - can be replaced with more sophisticated algorithms
        double sum = 0;
        for (double feature : features) {
            sum += feature;
        }
        return sum / features.length;
    }
}
