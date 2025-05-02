package org.projectk.circuitbreaker.ml.model;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Deque;
import java.util.ArrayDeque;
import lombok.extern.slf4j.Slf4j;
import org.projectk.circuitbreaker.ml.MetricSnapshot;

/**
 * Utility class for advanced feature engineering in the ML circuit breaker.
 * This class extracts, transforms, and augments raw metrics into rich feature sets
 * that capture complex patterns and interactions for improved prediction quality.
 */
@Slf4j
public class FeatureEngineering {
    // Feature indices for reference
    public static final int IDX_LATENCY = 0;
    public static final int IDX_ERROR_RATE = 1;
    public static final int IDX_CONCURRENCY = 2;
    public static final int IDX_SYSTEM_LOAD = 3;
    public static final int IDX_TIME_OF_DAY = 4;
    public static final int IDX_ERROR_TREND = 5;
    public static final int IDX_LATENCY_TREND = 6;
    public static final int IDX_STABILITY_SCORE = 7;
    public static final int IDX_LATENCY_ERROR_PRODUCT = 8;
    public static final int IDX_LATENCY_SQUARED = 9;
    public static final int IDX_CONCURRENCY_ERROR_PRODUCT = 10;
    public static final int IDX_LOAD_LATENCY_PRODUCT = 11;
    public static final int IDX_BUSINESS_HOURS = 12;
    public static final int IDX_NIGHTTIME = 13;
    public static final int IDX_RECENT_FAILURES = 14;
    
    // Number of features generated
    public static final int FEATURE_COUNT = 15;
    
    // Weights for stability score calculation
    private static final double ERROR_WEIGHT = 0.5;
    private static final double LATENCY_WEIGHT = 0.3;
    private static final double LOAD_WEIGHT = 0.2;
    
    // Constants for trend detection
    private static final int MAX_TREND_WINDOW = 10;
    
    // Recent metrics for trend calculation
    private final Queue<MetricSnapshot> recentMetrics = new LinkedList<>();
    
    // Recent features and targets for batch learning
    private final Deque<double[]> recentFeatures = new ArrayDeque<>();
    private final Deque<Double> recentTargets = new ArrayDeque<>();
    private static final int MAX_TRAINING_MEMORY = 100; // Max number of examples to store
    
    /**
     * Extracts and engineers an enhanced feature vector from a metric snapshot
     * 
     * @param metrics Current metric snapshot
     * @return Feature vector with basic and derived features
     */
    public double[] extractFeatures(MetricSnapshot metrics) {
        // Add to trend window
        recentMetrics.add(metrics);
        if (recentMetrics.size() > MAX_TREND_WINDOW) {
            recentMetrics.poll();
        }
        
        // Basic metrics with normalization
        double latency = metrics.getP95Latency() / 1000.0; // Normalize to seconds
        double errorRate = metrics.getErrorRate();
        double concurrency = metrics.getConcurrency() / 10.0; // Scale down
        double load = metrics.getSystemLoad() / 10.0; // Scale down
        double timeOfDay = metrics.getTimeOfDay();
        
        // Calculate trends and stability
        double errorTrend = calculateErrorRateTrend();
        double latencyTrend = calculateLatencyTrend();
        double stabilityScore = calculateStabilityScore(metrics);
        
        // Feature interactions and transformations
        double latencyErrorProduct = latency * errorRate;
        double latencySquared = latency * latency;
        double concurrencyErrorProduct = concurrency * errorRate;
        double loadLatencyProduct = load * latency;
        
        // Time-based features
        double isBusinessHours = (timeOfDay >= 0.33 && timeOfDay <= 0.75) ? 1.0 : 0.0;
        double isNighttime = (timeOfDay <= 0.25 || timeOfDay >= 0.875) ? 1.0 : 0.0;
        
        // Failure recency (exponential decay of failure rate over time)
        double recentFailures = calculateRecentFailureMetric();
        
        // Log feature creation occasionally
        if (Math.random() < 0.05) { // ~5% of calls
            logFeatureDetails(metrics, errorTrend, latencyTrend, stabilityScore);
        }
        
        // Return all features in array
        return new double[] {
            latency,
            errorRate,
            concurrency,
            load,
            timeOfDay,
            errorTrend,
            latencyTrend,
            stabilityScore,
            latencyErrorProduct,
            latencySquared,
            concurrencyErrorProduct,
            loadLatencyProduct,
            isBusinessHours,
            isNighttime,
            recentFailures
        };
    }
    
    /**
     * Calculates trend in error rate by comparing recent metrics
     * Positive values indicate increasing error rates (worsening)
     * Negative values indicate decreasing error rates (improving)
     * 
     * @return Normalized trend value between -1.0 and 1.0
     */
    public double calculateErrorRateTrend() {
        if (recentMetrics.size() < 2) {
            return 0.0; // Not enough data for trend
        }
        
        double oldestErrorRate = ((LinkedList<MetricSnapshot>)recentMetrics).getFirst().getErrorRate();
        double newestErrorRate = ((LinkedList<MetricSnapshot>)recentMetrics).getLast().getErrorRate();
        
        // Calculate exponential weighted trend if we have more data points
        if (recentMetrics.size() >= 3) {
            double weightedSum = 0;
            double weightSum = 0;
            double weight = 1.0;
            
            for (MetricSnapshot metrics : recentMetrics) {
                double err = metrics.getErrorRate();
                weightedSum += err * weight;
                weightSum += weight;
                weight *= 0.8; // Exponential decay for older samples
            }
            
            double weightedAvg = weightedSum / weightSum;
            return Math.max(-1.0, Math.min(1.0, (newestErrorRate - weightedAvg) * 5.0));
        }
        
        // Simple trend with just two points
        return Math.max(-1.0, Math.min(1.0, (newestErrorRate - oldestErrorRate) * 5.0));
    }
    
    /**
     * Calculates trend in latency by comparing recent metrics
     * 
     * @return Normalized trend value between -1.0 and 1.0
     */
    public double calculateLatencyTrend() {
        if (recentMetrics.size() < 2) {
            return 0.0; // Not enough data for trend
        }
        
        double oldestLatency = ((LinkedList<MetricSnapshot>)recentMetrics).getFirst().getP95Latency();
        double newestLatency = ((LinkedList<MetricSnapshot>)recentMetrics).getLast().getP95Latency();
        
        // Normalize by a reasonable maximum latency change (500ms)
        return Math.max(-1.0, Math.min(1.0, (newestLatency - oldestLatency) / 500.0));
    }
    
    /**
     * Calculates a composite stability score based on multiple metrics
     * Higher is better (more stable)
     * 
     * @param metrics Current metric snapshot
     * @return Stability score between 0.0 and 1.0
     */
    public double calculateStabilityScore(MetricSnapshot metrics) {
        // Error rate component (lower is better)
        double errorComponent = 1.0 - metrics.getErrorRate();
        
        // Latency component (lower is better)
        // Normalize to 0-1 where 1 is best
        double latencyNormalized = Math.max(0.0, 1.0 - (metrics.getP95Latency() / 2000.0));
        
        // System load component (medium is best)
        // Ideal load is around 0.5-0.7 of system capacity
        double loadFactor = metrics.getSystemLoad() / 10.0; // Normalize to 0-1
        double loadComponent = 1.0 - Math.abs(0.6 - loadFactor) * 1.5; // Penalize deviation from ideal
        loadComponent = Math.max(0.0, Math.min(1.0, loadComponent));
        
        // Calculate variance in recent metrics if available
        double varianceComponent = calculateVarianceComponent();
        
        // Composite score with weights
        double stabilityScore = (errorComponent * ERROR_WEIGHT) +
                               (latencyNormalized * LATENCY_WEIGHT) +
                               (loadComponent * LOAD_WEIGHT);
        
        // Apply variance penalty if we have enough data
        if (varianceComponent >= 0.0) {
            stabilityScore = stabilityScore * (0.8 + 0.2 * varianceComponent);
        }
        
        return Math.max(0.0, Math.min(1.0, stabilityScore));
    }
    
    /**
     * Calculates a component that penalizes high variance in metrics
     * 
     * @return Normalized variance component (higher = more stable)
     */
    private double calculateVarianceComponent() {
        if (recentMetrics.size() < 3) {
            return -1.0; // Not enough data
        }
        
        // Calculate variance in error rates
        double errorSum = 0.0;
        double errorSumSquared = 0.0;
        double latencySum = 0.0;
        double latencySumSquared = 0.0;
        
        for (MetricSnapshot metrics : recentMetrics) {
            double error = metrics.getErrorRate();
            double latency = metrics.getP95Latency() / 1000.0;
            
            errorSum += error;
            errorSumSquared += error * error;
            latencySum += latency;
            latencySumSquared += latency * latency;
        }
        
        int n = recentMetrics.size();
        double errorVariance = (errorSumSquared / n) - Math.pow(errorSum / n, 2);
        double latencyVariance = (latencySumSquared / n) - Math.pow(latencySum / n, 2);
        
        // Normalize variances
        double normalizedErrorVariance = Math.min(1.0, errorVariance * 20.0);
        double normalizedLatencyVariance = Math.min(1.0, latencyVariance * 5.0);
        
        // Higher variance = less stable, so we invert
        return 1.0 - (normalizedErrorVariance * 0.6 + normalizedLatencyVariance * 0.4);
    }
    
    /**
     * Calculates a metric for recent failures that decays over time
     * 
     * @return Value between 0.0 and 1.0 indicating recent failure activity
     */
    private double calculateRecentFailureMetric() {
        if (recentMetrics.size() < 2) {
            return 0.0;
        }
        
        double recentFailureSignal = 0.0;
        double decayFactor = 1.0;
        double weightSum = 0.0;
        
        for (MetricSnapshot metrics : ((LinkedList<MetricSnapshot>)recentMetrics)) {
            recentFailureSignal += metrics.getErrorRate() * decayFactor;
            weightSum += decayFactor;
            decayFactor *= 0.7; // Exponential decay for older samples
        }
        
        if (weightSum > 0) {
            recentFailureSignal /= weightSum;
        }
        
        return Math.min(1.0, recentFailureSignal * 2.0); // Amplify signal
    }
    
    /**
     * Logs detailed feature information for debugging
     */
    private void logFeatureDetails(MetricSnapshot metrics, double errorTrend, 
                                 double latencyTrend, double stabilityScore) {
        log.debug("Feature details: latency={}, errorRate={}, errorTrend={}, latencyTrend={}, stability={}",
                String.format("%.2f", metrics.getP95Latency() / 1000.0),
                String.format("%.3f", metrics.getErrorRate()),
                String.format("%.2f", errorTrend), 
                String.format("%.2f", latencyTrend),
                String.format("%.2f", stabilityScore));
    }
    
    /**
     * Records a feature vector and target value for batch learning
     * 
     * @param features Feature vector to store
     * @param target Target value to store
     */
    public void recordTrainingExample(double[] features, double target) {
        // Store feature vector (make a copy to avoid reference issues)
        double[] featureCopy = new double[features.length];
        System.arraycopy(features, 0, featureCopy, 0, features.length);
        recentFeatures.addLast(featureCopy);
        
        // Store target value
        recentTargets.addLast(target);
        
        // Maintain maximum size
        if (recentFeatures.size() > MAX_TRAINING_MEMORY) {
            recentFeatures.removeFirst();
            recentTargets.removeFirst();
        }
    }
    
    /**
     * Retrieves recent feature vectors for batch learning
     * 
     * @param count Number of recent examples to retrieve
     * @return Array of feature vectors, or null if not enough data
     */
    public double[][] getRecentFeatures(int count) {
        if (recentFeatures.size() < count) {
            return null; // Not enough examples stored
        }
        
        // Create array of most recent feature vectors
        double[][] batch = new double[count][FEATURE_COUNT];
        int i = 0;
        
        // Start from the end and go backward to get most recent examples
        Object[] featuresArray = recentFeatures.toArray();
        int startIdx = featuresArray.length - count;
        
        for (int j = startIdx; j < featuresArray.length; j++) {
            batch[i++] = (double[]) featuresArray[j];
        }
        
        return batch;
    }
    
    /**
     * Retrieves recent target values for batch learning
     * 
     * @param count Number of recent targets to retrieve
     * @return Array of target values, or null if not enough data
     */
    public double[] getRecentTargets(int count) {
        if (recentTargets.size() < count) {
            return null; // Not enough examples stored
        }
        
        // Create array of most recent target values
        double[] batch = new double[count];
        int i = 0;
        
        // Start from the end and go backward to get most recent targets
        Object[] targetsArray = recentTargets.toArray();
        int startIdx = targetsArray.length - count;
        
        for (int j = startIdx; j < targetsArray.length; j++) {
            batch[i++] = (Double) targetsArray[j];
        }
        
        return batch;
    }
    
    /**
     * Clears all stored training examples
     */
    public void clearTrainingMemory() {
        recentFeatures.clear();
        recentTargets.clear();
        log.debug("Cleared training memory cache");
    }
}
