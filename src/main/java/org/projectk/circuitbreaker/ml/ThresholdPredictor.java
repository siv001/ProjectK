package org.projectk.circuitbreaker.ml;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.projectk.circuitbreaker.ml.model.AnomalyDetector;
import org.projectk.circuitbreaker.ml.model.EnsemblePredictor;
import org.projectk.circuitbreaker.ml.model.FeatureEngineering;
import org.projectk.circuitbreaker.ml.model.TimeSeriesForecaster;
import org.springframework.stereotype.Component;

/**
 * Enhanced ML-based threshold predictor that dynamically adjusts circuit breaker parameters
 * based on real-time system metrics and learned patterns.
 * 
 * This advanced implementation uses an ensemble of neural networks, combined with
 * time-series forecasting and anomaly detection to provide more accurate and
 * adaptive circuit breaker configurations.
 */
@Slf4j
@Component
public class ThresholdPredictor {

    // Constants for parameter bounds
    private static final int MIN_WINDOW_SIZE = 10;
    private static final int MAX_WINDOW_SIZE = 100;
    private static final double MIN_THRESHOLD = 0.2;
    private static final double MAX_THRESHOLD = 0.8;
    private static final int MIN_WAIT_DURATION = 1000;
    private static final int MAX_WAIT_DURATION = 60000;
    
    // Constants for learning target weights
    private static final double SUCCESS_RATE_WEIGHT = 0.6;
    private static final double LATENCY_WEIGHT = 0.3;
    private static final double STABILITY_WEIGHT = 0.1;
    
    // Advanced ML components
    private final EnsemblePredictor ensembleModel;
    private final TimeSeriesForecaster timeSeriesModel;
    private final AnomalyDetector anomalyDetector;
    private final FeatureEngineering featureEngineering;
    
    // Parameter predictions
    private AtomicReference<Integer> windowSizePrediction = new AtomicReference<>(MIN_WINDOW_SIZE);
    private AtomicReference<Double> failureThresholdPrediction = new AtomicReference<>(0.5);
    private AtomicReference<Integer> waitDurationPrediction = new AtomicReference<>(MIN_WAIT_DURATION);
    
    // Last prediction and features for logging/diagnostics
    private double lastPrediction = 0.5;
    private double[] lastFeatures = null;
    private long predictionCount = 0;
    
    /**
     * Creates a new ML-based threshold predictor with enhanced learning capabilities
     */
    public ThresholdPredictor() {
        // Initialize feature engineering
        this.featureEngineering = new FeatureEngineering();
        
        // Initialize ensemble model with 3 neural networks
        this.ensembleModel = new EnsemblePredictor(3, FeatureEngineering.FEATURE_COUNT, 0.01);
        
        // Initialize time series model with AR(5) and MA(3)
        this.timeSeriesModel = new TimeSeriesForecaster(5, 3);
        
        // Initialize anomaly detector
        this.anomalyDetector = new AnomalyDetector(FeatureEngineering.FEATURE_COUNT, 30, 2.5);
        
        log.info("Initialized Enhanced ThresholdPredictor with neural ensemble, time series forecasting, and anomaly detection");
    }
    
    /**
     * Updates thresholds based on current metrics using enhanced ML components
     * 
     * @param metrics Current system metrics snapshot
     */
    public void updateThresholds(MetricSnapshot metrics) {
        predictionCount++;
        
        // Extract enhanced features with interactions and transformations
        double[] features = featureEngineering.extractFeatures(metrics);
        lastFeatures = features;
        
        // Get time-series forecast based on historical patterns
        double timeSeriesPrediction = timeSeriesModel.forecast();
        
        // Get ensemble prediction from neural networks
        double ensemblePrediction = ensembleModel.predict(features);
        
        // Calculate anomaly score to detect unusual situations
        anomalyDetector.processDataPoint(features);
        double anomalyScore = anomalyDetector.getAnomalyScore(features);
        
        // Combine predictions with weighting
        // - Give more weight to ensemble when we have enough data
        // - Rely more on time series when we detect anomalies
        double ensembleWeight = Math.min(0.8, 0.4 + (0.4 * Math.min(1.0, predictionCount / 100.0)));
        
        // If highly anomalous, trust time series more and be conservative
        if (anomalyScore > 0.8) {
            ensembleWeight *= (1.0 - (anomalyScore - 0.8) * 0.5);
        }
        
        // Combine predictions with determined weights
        double timeSeriesWeight = 1.0 - ensembleWeight;
        lastPrediction = (ensemblePrediction * ensembleWeight) + 
                       (timeSeriesPrediction * timeSeriesWeight);
        
        // Update specific circuit breaker parameters
        updateSpecificPredictions(metrics, lastPrediction);
        
        // Calculate actual outcomes for learning
        double[] actual = calculateActualOutcomes(metrics);
        
        // Record features and targets for potential batch learning
        featureEngineering.recordTrainingExample(features, actual[0]);
        
        // Check if we should do individual or batch learning
        if (predictionCount % 10 == 0 && predictionCount > 10) {
            // Every 10 predictions, perform batch learning with recent examples
            double[][] batchFeatures = featureEngineering.getRecentFeatures(10);
            double[] batchTargets = featureEngineering.getRecentTargets(10);
            
            if (batchFeatures != null && batchTargets != null) {
                // Use batch learning for faster and more stable updates
                ensembleModel.learnBatch(batchFeatures, batchTargets);
                log.debug("Performed batch learning with {} examples", batchFeatures.length);
            }
        } else {
            // Normal individual example learning
            ensembleModel.learn(features, actual[0]);
        }
        
        // Always update time series model with latest data
        timeSeriesModel.update(actual[0]);
        
        // Log detailed prediction info periodically
        if (predictionCount % 10 == 0 || anomalyScore > 0.8) {
            logPredictionDetails(metrics, ensemblePrediction, timeSeriesPrediction, 
                              anomalyScore, ensembleWeight);
        }
    }
    
    /**
     * Updates specific circuit breaker parameter predictions based on the composite score
     */
    private void updateSpecificPredictions(MetricSnapshot metrics, double basePrediction) {
        // Add influence from error rate trend for more responsive behavior
        double errorTrend = featureEngineering.calculateErrorRateTrend();
        double latencyTrend = featureEngineering.calculateLatencyTrend();
        
        // Adjust prediction based on trends - be more conservative when things are worsening
        double predictorScore = basePrediction;
        
        // If error rate is increasing rapidly, be more conservative
        if (errorTrend > 0.3) {
            predictorScore *= (1.0 - (errorTrend - 0.3) * 0.5);
        }
        
        // If latency is increasing rapidly, also be more conservative
        if (latencyTrend > 0.3) {
            predictorScore *= (1.0 - (latencyTrend - 0.3) * 0.3);
        }
        
        // Keep in valid range
        predictorScore = Math.max(0.0, Math.min(1.0, predictorScore));
        
        // Window size should be larger when system is unstable (lower prediction values)
        // This gives more statistical confidence when making circuit breaker decisions
        int newWindowSize = (int)(MIN_WINDOW_SIZE + 
            (1 - predictorScore) * (MAX_WINDOW_SIZE - MIN_WINDOW_SIZE));
            
        // Failure threshold should be lower (stricter) when system shows instability
        // This makes the circuit breaker more sensitive when needed
        double newFailureThreshold = MIN_THRESHOLD +
            predictorScore * (MAX_THRESHOLD - MIN_THRESHOLD);
            
        // Wait duration should be longer when system is unstable to allow for recovery
        // This prevents premature circuit closing when system needs time to recover
        int newWaitDuration = (int)(MIN_WAIT_DURATION +
            (1 - predictorScore) * (MAX_WAIT_DURATION - MIN_WAIT_DURATION));
        
        // Update atomic references atomically
        windowSizePrediction.set(newWindowSize);
        failureThresholdPrediction.set(newFailureThreshold);
        waitDurationPrediction.set(newWaitDuration);
    }
    
    /**
     * Calculates a composite learning target by combining success rate, latency,
     * and stability metrics into a weighted score.
     */
    private double[] calculateActualOutcomes(MetricSnapshot metrics) {
        // Success rate component (higher is better)
        double successRateScore = metrics.getSuccessRate();
        
        // Latency component (lower is better, normalized to 0-1 where 1 is best)
        // Assume latencies over 2000ms are problematic
        double latencyScore = Math.max(0, 1 - (metrics.getP95Latency() / 2000.0));
        
        // System stability component based on load and trends
        double stabilityScore = featureEngineering.calculateStabilityScore(metrics);
        
        // Combined weighted score using importance weights
        double compositeScore = (successRateScore * SUCCESS_RATE_WEIGHT) +
                            (latencyScore * LATENCY_WEIGHT) +
                            (stabilityScore * STABILITY_WEIGHT);
                            
        // Normalize final score to 0-1 range
        compositeScore = Math.min(1.0, Math.max(0.0, compositeScore));
        
        if (log.isDebugEnabled()) {
            log.debug("Learning target calculated: success={}, latency={}, stability={} → composite={}",
                     String.format("%.2f", successRateScore),
                     String.format("%.2f", latencyScore),
                     String.format("%.2f", stabilityScore),
                     String.format("%.4f", compositeScore));
        }
        
        return new double[] { compositeScore };
    }
    
    /**
     * Logs detailed information about the prediction process
     */
    private void logPredictionDetails(MetricSnapshot metrics, double ensemblePrediction, 
                                   double timeSeriesPrediction, double anomalyScore, 
                                   double ensembleWeight) {
        log.debug("ML prediction #{}: ensemble={}, timeSeries={}, anomalyScore={}, final={}",
                 predictionCount,
                 String.format("%.4f", ensemblePrediction),
                 String.format("%.4f", timeSeriesPrediction),
                 String.format("%.4f", anomalyScore),
                 String.format("%.4f", lastPrediction));
                 
        log.debug("Circuit breaker parameters: windowSize={}, failureThreshold={}, waitDuration={}ms",
                 windowSizePrediction.get(),
                 String.format("%.2f", failureThresholdPrediction.get()),
                 waitDurationPrediction.get());
    }

    /**
     * @return Current predicted rolling window size
     */
    public int getWindowSize() {
        return windowSizePrediction.get();
    }

    /**
     * @return Current predicted failure threshold
     */
    public double getFailureThreshold() {
        return failureThresholdPrediction.get();
    }

    /**
     * @return Current predicted wait duration in milliseconds
     */
    public int getWaitDuration() {
        return waitDurationPrediction.get();
    }
    
    /**
     * @return Wait duration in seconds formatted for circuit breaker configuration
     */
    public int getWaitDurationInSeconds() {
        return (int) TimeUnit.MILLISECONDS.toSeconds(waitDurationPrediction.get());
    }
    
    /**
     * @return Latest composite prediction score (0-1)
     */
    public double getLastPrediction() {
        return lastPrediction;
    }
    
    /**
     * Gets a detailed model explanation including the influence of each feature
     * 
     * @return Human-readable explanation of the current prediction
     */
    public String getModelExplanation() {
        if (lastFeatures == null) {
            return "No prediction has been made yet";
        }
        
        StringBuilder explanation = new StringBuilder();
        explanation.append("ML Model Explanation:\n");
        
        // Overall prediction quality
        explanation.append(String.format("Overall prediction quality: %.2f\n", lastPrediction));
        
        // Anomaly detection
        double anomalyScore = anomalyDetector.getAnomalyScore(lastFeatures);
        String anomalyStatus = anomalyScore > 0.8 ? "HIGH" : 
                             (anomalyScore > 0.5 ? "MEDIUM" : "LOW");
        explanation.append(String.format("Anomaly level: %s (%.2f)\n", anomalyStatus, anomalyScore));
        
        // Key feature influences
        explanation.append("Key influencing factors:\n");
        
        // Error rate and trend
        explanation.append(String.format("- Error rate: %.3f ", lastFeatures[FeatureEngineering.IDX_ERROR_RATE]));
        if (lastFeatures[FeatureEngineering.IDX_ERROR_TREND] > 0.1) {
            explanation.append("(INCREASING ↑)");
        } else if (lastFeatures[FeatureEngineering.IDX_ERROR_TREND] < -0.1) {
            explanation.append("(DECREASING ↓)");
        } else {
            explanation.append("(STABLE →)");
        }
        explanation.append("\n");
        
        // Latency and trend
        explanation.append(String.format("- Latency: %.2f ms ", lastFeatures[FeatureEngineering.IDX_LATENCY] * 1000));
        if (lastFeatures[FeatureEngineering.IDX_LATENCY_TREND] > 0.1) {
            explanation.append("(INCREASING ↑)");
        } else if (lastFeatures[FeatureEngineering.IDX_LATENCY_TREND] < -0.1) {
            explanation.append("(DECREASING ↓)");
        } else {
            explanation.append("(STABLE →)");
        }
        explanation.append("\n");
        
        // System load
        explanation.append(String.format("- System load: %.1f\n", 
                         lastFeatures[FeatureEngineering.IDX_SYSTEM_LOAD] * 10));
        
        // Stability
        explanation.append(String.format("- System stability: %.2f\n", 
                         lastFeatures[FeatureEngineering.IDX_STABILITY_SCORE]));
        
        // Circuit breaker parameters
        explanation.append("\nCircuit breaker configuration:\n");
        explanation.append(String.format("- Window size: %d\n", windowSizePrediction.get()));
        explanation.append(String.format("- Failure threshold: %.2f\n", failureThresholdPrediction.get()));
        explanation.append(String.format("- Wait duration: %d ms\n", waitDurationPrediction.get()));
        
        return explanation.toString();
    }

    /**
     * @return optimal wait duration prediction as a Duration
     */
    public Duration getOptimalWaitDuration() {
        return Duration.ofMillis(waitDurationPrediction.get());
    }

    /**
     * @return optimal sliding window size prediction
     */
    public int getOptimalWindowSize() {
        return windowSizePrediction.get();
    }

    /**
     * @return optimal failure threshold prediction
     */
    public double getCurrentThreshold() {
        return failureThresholdPrediction.get();
    }
}
