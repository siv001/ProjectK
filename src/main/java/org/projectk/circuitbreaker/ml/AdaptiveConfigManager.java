package org.projectk.circuitbreaker.ml;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Manages dynamic configuration updates for circuit breakers based on ML predictions.
 * This enhanced version works with the neural network ensemble model for more accurate predictions.
 */
@Slf4j
@Component
public class AdaptiveConfigManager {

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private final ThresholdPredictor thresholdPredictor;
    
    /**
     * Creates an adaptive config manager with both circuit breaker registry and threshold predictor
     * 
     * @param circuitBreakerRegistry Registry for circuit breaker management
     * @param thresholdPredictor Predictor for optimal circuit breaker parameters
     */
    @Autowired
    public AdaptiveConfigManager(CircuitBreakerRegistry circuitBreakerRegistry, ThresholdPredictor thresholdPredictor) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.thresholdPredictor = thresholdPredictor;
    }
    
    /**
     * Creates an adaptive config manager with only the threshold predictor
     * Used when circuit breaker registry is managed externally
     * 
     * @param thresholdPredictor Predictor for optimal circuit breaker parameters
     */
    public AdaptiveConfigManager(ThresholdPredictor thresholdPredictor) {
        this.thresholdPredictor = thresholdPredictor;
    }
    
    private static final double SIGNIFICANT_CHANGE_THRESHOLD = 0.1; // 10% change is considered significant
    
    /**
     * Updates the circuit breaker configuration based on the latest ML predictions
     * 
     * @param circuitBreakerName Name of the circuit breaker to update
     * @param metrics Current metric snapshot
     * @return true if configuration was updated, false if no significant changes
     */
    public boolean updateCircuitBreakerConfig(String circuitBreakerName, MetricSnapshot metrics) {
        log.debug("Updating circuit breaker configuration for: {}", circuitBreakerName);
        
        // Update the ML model with current metrics
        thresholdPredictor.updateThresholds(metrics);
        
        // Ensure circuit breaker registry is available
        if (circuitBreakerRegistry == null) {
            log.warn("Circuit breaker registry not available, skipping config update");
            return false;
        }
        
        // Get the current circuit breaker
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        CircuitBreakerConfig currentConfig = circuitBreaker.getCircuitBreakerConfig();
        
        // Get predictions from the ML model
        int predictedWindowSize = thresholdPredictor.getWindowSize();
        float predictedFailureThreshold = (float)thresholdPredictor.getFailureThreshold();
        int predictedWaitDurationMs = thresholdPredictor.getWaitDuration();
        
        // Get current settings
        int currentWindowSize = currentConfig.getSlidingWindowSize();
        float currentFailureThreshold = currentConfig.getFailureRateThreshold();
        
        // In Resilience4j 2.x, the wait duration is handled as an IntervalFunction
        // We assume a constant wait time (no backoff) for simplicity
        IntervalFunction waitIntervalFunction = currentConfig.getWaitIntervalFunctionInOpenState();
        long currentWaitDurationMs = waitIntervalFunction.apply(1); // Get wait time for 1st attempt
        
        // Check if change is significant enough to apply
        // This prevents too frequent updates for minor changes
        boolean significantWindowSizeChange = isSignificantChange(currentWindowSize, predictedWindowSize);
        boolean significantThresholdChange = isSignificantChange(currentFailureThreshold, predictedFailureThreshold);
        boolean significantWaitDurationChange = isSignificantChange(currentWaitDurationMs, predictedWaitDurationMs);
        
        // If no significant changes, skip update
        if (!significantWindowSizeChange && !significantThresholdChange && !significantWaitDurationChange) {
            log.debug("No significant configuration changes for {}, skipping update", circuitBreakerName);
            return false;
        }
        
        // Build new config with ML-based values
        CircuitBreakerConfig newConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(predictedWindowSize)
                .failureRateThreshold(predictedFailureThreshold)
                // Use fixed interval function with our predicted wait time
                .waitIntervalFunctionInOpenState(IntervalFunction.of(Duration.ofMillis(predictedWaitDurationMs)))
                .permittedNumberOfCallsInHalfOpenState(currentConfig.getPermittedNumberOfCallsInHalfOpenState())
                .minimumNumberOfCalls(currentConfig.getMinimumNumberOfCalls())
                .slowCallRateThreshold(currentConfig.getSlowCallRateThreshold())
                .slowCallDurationThreshold(currentConfig.getSlowCallDurationThreshold())
                .build();
        
        // Apply the new configuration
        CircuitBreaker updatedBreaker = CircuitBreaker.of(circuitBreakerName, newConfig);
        circuitBreakerRegistry.replace(circuitBreakerName, updatedBreaker);
        
        // Log the changes with detailed explanation
        logConfigurationChange(circuitBreakerName, currentConfig, newConfig, metrics);
        
        return true;
    }
    
    /**
     * Determines if a change is significant enough to warrant a configuration update
     * 
     * @param currentValue Current parameter value
     * @param newValue New parameter value
     * @return true if change is significant
     */
    private boolean isSignificantChange(double currentValue, double newValue) {
        // Check if relative change exceeds the threshold
        if (currentValue == 0) {
            return newValue > 0;
        }
        
        double relativeChange = Math.abs(newValue - currentValue) / currentValue;
        return relativeChange > SIGNIFICANT_CHANGE_THRESHOLD;
    }
    
    /**
     * Gets an updated CircuitBreakerConfig based on ML predictions
     * This method is used by MLCircuitBreaker to get the new configuration
     * 
     * @param metrics Current metric snapshot
     * @return CircuitBreakerConfig with updated parameters based on predictions
     */
    public CircuitBreakerConfig getUpdatedConfig(MetricSnapshot metrics) {
        // Update the ML model with current metrics
        thresholdPredictor.updateThresholds(metrics);
        
        // Get predictions from the ML model
        int predictedWindowSize = thresholdPredictor.getWindowSize();
        float predictedFailureThreshold = (float)thresholdPredictor.getFailureThreshold();
        int predictedWaitDurationMs = thresholdPredictor.getWaitDuration();
        
        // Build new config with ML-based values
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(predictedWindowSize)
                .failureRateThreshold(predictedFailureThreshold)
                // Use fixed interval function with our predicted wait time
                .waitIntervalFunctionInOpenState(IntervalFunction.of(Duration.ofMillis(predictedWaitDurationMs)))
                .slowCallRateThreshold(50.0f)  // Fixed value for slow call rate threshold
                .minimumNumberOfCalls(10)     // Reasonable default for minimum calls
                .permittedNumberOfCallsInHalfOpenState(5)  // Default for half-open state
                .slowCallDurationThreshold(Duration.ofSeconds(1))  // Default slow call duration
                .build();
    }
    
    /**
     * Determines if changes between configs are significant enough to justify an update
     * Used by MLCircuitBreaker to avoid frequent updates for small changes
     * 
     * @param newConfig New circuit breaker configuration
     * @param newWaitDuration New wait duration
     * @param currentWaitDuration Current wait duration
     * @return true if any parameter has a significant change
     */
    public boolean isSignificantChange(CircuitBreakerConfig newConfig, Duration newWaitDuration, Duration currentWaitDuration) {
        // Check if wait duration has changed significantly
        boolean waitDurationChanged = isSignificantChange(
            currentWaitDuration.toMillis(), newWaitDuration.toMillis());
            
        // If wait duration has changed significantly, we consider it a significant change
        if (waitDurationChanged) {
            log.debug("Wait duration change is significant: {} -> {}", 
                     currentWaitDuration.toSeconds(), newWaitDuration.toSeconds());
            return true;
        }
        
        // We already checked for wait duration, so if we're here, window size and threshold
        // would need to have changed significantly for this to return true
        return false;
    }
    
    /**
     * Logs detailed information about configuration changes including explanation
     */
    private void logConfigurationChange(String circuitBreakerName, 
                                     CircuitBreakerConfig oldConfig, 
                                     CircuitBreakerConfig newConfig,
                                     MetricSnapshot metrics) {
        // For Resilience4j 2.x, get wait durations from the interval functions
        IntervalFunction oldWaitFunction = oldConfig.getWaitIntervalFunctionInOpenState();
        long oldWaitMs = oldWaitFunction.apply(1); // For first attempt
                
        IntervalFunction newWaitFunction = newConfig.getWaitIntervalFunctionInOpenState();
        long newWaitMs = newWaitFunction.apply(1); // For first attempt
                
        log.info("Updated circuit breaker configuration for {}", circuitBreakerName);
        log.info("Window size: {} → {}", 
                oldConfig.getSlidingWindowSize(), 
                newConfig.getSlidingWindowSize());
        log.info("Failure threshold: {}% → {}%", 
                String.format("%.1f", oldConfig.getFailureRateThreshold()),
                String.format("%.1f", newConfig.getFailureRateThreshold()));
        log.info("Wait duration: {}ms → {}ms", 
                oldWaitMs,
                newWaitMs);
        
        // Log metrics that influenced the decision
        log.info("Change influenced by metrics: errorRate={}, p95Latency={}ms, concurrency={}",
                String.format("%.3f", metrics.getErrorRate()),
                String.format("%.0f", metrics.getP95Latency()),
                metrics.getConcurrency());
        
        // Log detailed model explanation if available
        String modelExplanation = thresholdPredictor.getModelExplanation();
        if (modelExplanation != null && !modelExplanation.isEmpty()) {
            log.info("ML Model explanation:\n{}", modelExplanation);
        }
    }
}
