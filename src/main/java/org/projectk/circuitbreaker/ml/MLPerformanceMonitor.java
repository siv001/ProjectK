package org.projectk.circuitbreaker.ml;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Monitors and records ML prediction performance metrics
 */
@Slf4j
public class MLPerformanceMonitor {
    private final MeterRegistry registry;
    
    // Prediction accuracy tracking
    private final AtomicInteger totalPredictions = new AtomicInteger(0);
    private final AtomicInteger accuratePredictions = new AtomicInteger(0);
    private final AtomicReference<Double> averageError = new AtomicReference<>(0.0);
    
    // Parameter effectiveness tracking
    private final AtomicReference<Integer> lastWindowSize = new AtomicReference<>(100);
    private final AtomicReference<Float> lastThreshold = new AtomicReference<>(50.0f);
    private final AtomicReference<Duration> lastWaitDuration = new AtomicReference<>(Duration.ofSeconds(30));
    
    // Performance tracking
    private final AtomicReference<Double> beforeChangeErrorRate = new AtomicReference<>(0.0);
    private final AtomicReference<Double> afterChangeErrorRate = new AtomicReference<>(0.0);
    
    // Constants for accuracy evaluation
    private static final double ACCURATE_PREDICTION_THRESHOLD = 0.25; // Prediction within 25% of actual
    private static final int REPORT_INTERVAL = 100; // Report every 100 predictions

    /**
     * Creates a new performance monitor
     *
     * @param registry Micrometer registry for metrics collection
     */
    public MLPerformanceMonitor(MeterRegistry registry) {
        this.registry = registry;
        
        // Register gauges for long-term tracking
        registry.gauge("ml.prediction.accuracy.percent", 
                Collections.emptyList(), accuratePredictions, 
                value -> totalPredictions.get() > 0 ? 
                       (double)value.get() / totalPredictions.get() * 100 : 0.0);
        
        registry.gauge("ml.prediction.error.avg", 
                Collections.emptyList(), averageError, 
                AtomicReference::get);
                
        registry.gauge("ml.config.effectiveness", 
                Collections.emptyList(), this, 
                monitor -> calculateEffectiveness());
    }

    /**
     * Records the accuracy of a prediction compared to the actual outcome
     *
     * @param actual True if the operation succeeded, false otherwise
     * @param predicted The probability of success predicted by the model
     */
    public void recordPredictionAccuracy(boolean actual, double predicted) {
        // Calculate prediction error
        double actualValue = actual ? 1.0 : 0.0;
        double error = Math.abs(actualValue - predicted);
        
        // Update running error average
        double currentAvg = averageError.get();
        int total = totalPredictions.get();
        double newAvg = (currentAvg * total + error) / (total + 1);
        averageError.set(newAvg);
        
        // Check if prediction was accurate within threshold
        if (error < ACCURATE_PREDICTION_THRESHOLD) {
            accuratePredictions.incrementAndGet();
        }
        
        // Increment total predictions
        int newTotal = totalPredictions.incrementAndGet();
        
        // Record detailed metrics
        registry.gauge("ml.prediction.error.last", error);
        registry.gauge("ml.prediction.actual", actualValue);
        registry.gauge("ml.prediction.forecast", predicted);
        
        // Periodically log accuracy stats
        if (newTotal % REPORT_INTERVAL == 0) {
            double accuracyPct = (double)accuratePredictions.get() / newTotal * 100;
            log.info("ML prediction accuracy: {}% ({}/{} correct), avg error: {}", 
                    String.format("%.1f", accuracyPct),
                    accuratePredictions.get(), 
                    newTotal,
                    String.format("%.3f", newAvg));
        }
    }

    /**
     * Records various model features for monitoring
     *
     * @param metrics Current metrics snapshot
     */
    public void recordModelMetrics(MetricSnapshot metrics) {
        registry.gauge("ml.feature.latency", metrics.getP95Latency());
        registry.gauge("ml.feature.error_rate", metrics.getErrorRate());
        registry.gauge("ml.feature.concurrency", metrics.getConcurrency());
        registry.gauge("ml.feature.system_load", metrics.getSystemLoad());
        
        // Track error rate for effectiveness calculations
        afterChangeErrorRate.set(metrics.getErrorRate());
    }
    
    /**
     * Records when circuit breaker parameters change
     * to evaluate their effectiveness
     * 
     * @param windowSize New window size
     * @param threshold New failure threshold
     * @param waitDuration New wait duration
     * @param currentErrorRate Error rate at time of change
     */
    public void recordParameterChange(int windowSize, float threshold, Duration waitDuration, double currentErrorRate) {
        // Store previous values for tracking
        lastWindowSize.set(windowSize);
        lastThreshold.set(threshold);
        lastWaitDuration.set(waitDuration);
        
        // Record current error rate as "before change"
        beforeChangeErrorRate.set(currentErrorRate);
        
        // Record gauges for the parameter values
        registry.gauge("ml.config.window_size", windowSize);
        registry.gauge("ml.config.threshold", threshold);
        registry.gauge("ml.config.wait_duration", waitDuration.getSeconds());
        
        log.info("Circuit breaker parameters changed: window={}, threshold={}%, waitDuration={}s, current_error_rate={}%",
                windowSize, threshold, waitDuration.getSeconds(), String.format("%.2f", currentErrorRate * 100));
    }
    
    /**
     * Calculates the effectiveness of the ML configuration
     * by comparing error rates before and after parameter changes
     * 
     * @return Effectiveness score (negative is good, positive is bad)
     */
    private double calculateEffectiveness() {
        double before = beforeChangeErrorRate.get();
        double after = afterChangeErrorRate.get();
        
        // Change in error rate (negative values are improvements)
        double effectiveness = after - before;
        
        if (log.isDebugEnabled() && Math.abs(effectiveness) > 0.05) {
            if (effectiveness < 0) {
                log.debug("ML config improving: error rate decreased by {}% after parameter change", 
                        String.format("%.2f", Math.abs(effectiveness) * 100));
            } else {
                log.debug("ML config needs tuning: error rate increased by {}% after parameter change", 
                        String.format("%.2f", effectiveness * 100));
            }
        }
        
        return effectiveness;
    }
    
    /**
     * Logs a comprehensive performance report for the ML circuit breaker
     */
    public void logPerformanceReport() {
        int total = totalPredictions.get();
        if (total == 0) {
            log.info("No ML prediction data available yet for performance report");
            return;
        }
        
        double accuracyPct = (double)accuratePredictions.get() / total * 100;
        double effectivenessScore = calculateEffectiveness();
        String effectivenessDesc = effectivenessScore < 0 ? "IMPROVING" : 
                                  (effectivenessScore > 0.05 ? "DEGRADING" : "NEUTRAL");
        
        log.info("\n===== ML CIRCUIT BREAKER PERFORMANCE REPORT =====\n" +
                "Prediction Accuracy: {}% ({}/{} correct)\n" +
                "Average Error: {}\n" +
                "Current Parameters: window={}, threshold={}%, waitDuration={}s\n" +
                "Effectiveness: {} ({}%)\n" +
                "=================================================",
                String.format("%.1f", accuracyPct),
                accuratePredictions.get(),
                total,
                String.format("%.3f", averageError.get()),
                lastWindowSize.get(),
                lastThreshold.get(),
                lastWaitDuration.get().getSeconds(),
                effectivenessDesc,
                String.format("%.2f", Math.abs(effectivenessScore) * 100));
    }
}
