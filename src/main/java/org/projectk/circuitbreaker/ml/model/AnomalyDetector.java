package org.projectk.circuitbreaker.ml.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Simple anomaly detector that identifies outliers in feature data.
 * Uses a statistical approach based on standard deviations from the mean
 * for each feature dimension, which is more lightweight than a full
 * isolation forest implementation but still effective for detecting anomalies.
 */
@Slf4j
public class AnomalyDetector {
    private final int numFeatures;
    private final double[] means;
    private final double[] stdDevs;
    private final double[] minValues;
    private final double[] maxValues;
    private final List<double[]> recentSamples;
    private final int maxSamples;
    private final double anomalyThreshold;
    
    private long dataPointsProcessed = 0;
    
    /**
     * Creates an anomaly detector
     * 
     * @param numFeatures Number of features in input data
     * @param maxSamples Number of recent samples to maintain
     * @param anomalyThreshold Threshold for detecting anomalies (typically 2.0-3.0)
     */
    public AnomalyDetector(int numFeatures, int maxSamples, double anomalyThreshold) {
        this.numFeatures = numFeatures;
        this.means = new double[numFeatures];
        this.stdDevs = new double[numFeatures];
        Arrays.fill(stdDevs, 1.0); // Initialize to reasonable default
        this.minValues = new double[numFeatures];
        Arrays.fill(minValues, Double.MAX_VALUE);
        this.maxValues = new double[numFeatures];
        Arrays.fill(maxValues, Double.MIN_VALUE);
        this.recentSamples = new ArrayList<>(maxSamples);
        this.maxSamples = maxSamples;
        this.anomalyThreshold = anomalyThreshold;
        
        log.debug("Initialized AnomalyDetector with numFeatures={}, threshold={}", 
                numFeatures, anomalyThreshold);
    }
    
    /**
     * Processes a new data point and updates statistics
     * 
     * @param features Feature vector to process
     */
    public void processDataPoint(double[] features) {
        if (features.length != numFeatures) {
            log.warn("Feature vector length {} doesn't match expected size {}", 
                    features.length, numFeatures);
            return;
        }
        
        // Add to recent samples
        double[] featureCopy = Arrays.copyOf(features, features.length);
        recentSamples.add(featureCopy);
        
        // Maintain fixed size
        if (recentSamples.size() > maxSamples) {
            recentSamples.remove(0);
        }
        
        // Update statistics incrementally
        updateStatistics(features);
        dataPointsProcessed++;
        
        // Log stats occasionally
        if (dataPointsProcessed % 100 == 0) {
            log.debug("Anomaly detector has processed {} data points", dataPointsProcessed);
        }
    }
    
    /**
     * Updates statistics with new data point
     */
    private void updateStatistics(double[] features) {
        // First update min/max values
        for (int i = 0; i < numFeatures; i++) {
            minValues[i] = Math.min(minValues[i], features[i]);
            maxValues[i] = Math.max(maxValues[i], features[i]);
        }
        
        // If we have few samples, just recalculate everything
        if (recentSamples.size() <= 10) {
            recalculateStatistics();
            return;
        }
        
        // Otherwise, update incrementally
        double learningRate = 0.1; // Control how quickly stats adapt
        for (int i = 0; i < numFeatures; i++) {
            // Update mean with exponential moving average
            double oldMean = means[i];
            means[i] = oldMean + learningRate * (features[i] - oldMean);
            
            // Update variance estimate
            double variance = stdDevs[i] * stdDevs[i];
            variance = (1 - learningRate) * variance + 
                      learningRate * Math.pow(features[i] - means[i], 2);
            stdDevs[i] = Math.sqrt(Math.max(0.0001, variance)); // Avoid division by zero
        }
    }
    
    /**
     * Recalculates statistics from scratch using all recent samples
     */
    private void recalculateStatistics() {
        if (recentSamples.isEmpty()) {
            return;
        }
        
        // Reset means and prepare for variance calculation
        Arrays.fill(means, 0.0);
        double[] sumSquared = new double[numFeatures];
        
        // Calculate means
        for (double[] sample : recentSamples) {
            for (int i = 0; i < numFeatures; i++) {
                means[i] += sample[i];
            }
        }
        
        for (int i = 0; i < numFeatures; i++) {
            means[i] /= recentSamples.size();
        }
        
        // Calculate variances
        for (double[] sample : recentSamples) {
            for (int i = 0; i < numFeatures; i++) {
                sumSquared[i] += Math.pow(sample[i] - means[i], 2);
            }
        }
        
        for (int i = 0; i < numFeatures; i++) {
            stdDevs[i] = Math.sqrt(Math.max(0.0001, sumSquared[i] / recentSamples.size()));
        }
    }
    
    /**
     * Calculates anomaly score for a feature vector
     * Higher scores indicate more anomalous data
     * 
     * @param features Feature vector to score
     * @return Anomaly score, where values > 1.0 are considered anomalous
     */
    public double getAnomalyScore(double[] features) {
        if (features.length != numFeatures || recentSamples.isEmpty()) {
            return 0.0;
        }
        
        // Mahalanobis-inspired distance calculation
        double sumSquaredDeviations = 0.0;
        for (int i = 0; i < numFeatures; i++) {
            // Skip features with no variance
            if (stdDevs[i] <= 0.0001) continue;
            
            double deviation = (features[i] - means[i]) / stdDevs[i];
            sumSquaredDeviations += deviation * deviation;
        }
        
        // Normalize by number of features
        double score = Math.sqrt(sumSquaredDeviations / numFeatures);
        
        if (score > anomalyThreshold && dataPointsProcessed > 20) {
            log.debug("Detected anomaly with score {}", String.format("%.2f", score));
        }
        
        return score / anomalyThreshold; // Normalize to threshold
    }
    
    /**
     * Determines if a feature vector is anomalous
     * 
     * @param features Feature vector to check
     * @return true if anomalous, false otherwise
     */
    public boolean isAnomaly(double[] features) {
        return getAnomalyScore(features) > 1.0;
    }
    
    /**
     * @return Number of data points processed so far
     */
    public long getDataPointsProcessed() {
        return dataPointsProcessed;
    }
}
