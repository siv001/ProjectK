package org.projectk.circuitbreaker.ml;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdaptiveConfigManagerTest {

    private AdaptiveConfigManager configManager;
    
    @Mock
    private ThresholdPredictor predictor;
    
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    
    @Mock
    private CircuitBreaker circuitBreaker;
    
    @Mock
    private MetricSnapshot metrics;
    
    @BeforeEach
    void setUp() {
        // Create the config manager with both dependencies
        configManager = new AdaptiveConfigManager(circuitBreakerRegistry, predictor);
    }
    
    @Test
    @DisplayName("Should create config with values from predictor")
    void shouldCreateConfigWithValuesFromPredictor() {
        // Given
        double expectedFailureThreshold = 75.0;
        int expectedWindowSize = 200;
        Duration expectedWaitDuration = Duration.ofSeconds(45);
        int expectedWaitDurationMs = (int)expectedWaitDuration.toMillis();
        
        when(predictor.getFailureThreshold()).thenReturn(expectedFailureThreshold);
        when(predictor.getWindowSize()).thenReturn(expectedWindowSize);
        when(predictor.getWaitDuration()).thenReturn(expectedWaitDurationMs);
        
        // When
        CircuitBreakerConfig config = configManager.getUpdatedConfig(metrics);
        
        // Then
        assertThat(config).isNotNull();
        assertThat(config.getFailureRateThreshold()).isEqualTo((float)expectedFailureThreshold);
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getSlidingWindowSize()).isEqualTo(expectedWindowSize);
    }
    
    @Test
    @DisplayName("Should use predictor with the provided metrics")
    void shouldUsePredictorWithProvidedMetrics() {
        // Given
        // Default values for predictor
        when(predictor.getFailureThreshold()).thenReturn(50.0);
        when(predictor.getWindowSize()).thenReturn(100);
        when(predictor.getWaitDuration()).thenReturn(1000);
        
        // When
        configManager.getUpdatedConfig(metrics);
        
        // Then
        verify(predictor).updateThresholds(metrics);
    }
    
    @Test
    @DisplayName("Should build valid circuit breaker config when metrics are empty")
    void shouldBuildValidCircuitBreakerConfigWhenMetricsAreEmpty() {
        // Given
        MetricSnapshot emptyMetrics = new MetricSnapshot(Collections.emptyList());
        
        // Default predictor behavior
        when(predictor.getFailureThreshold()).thenReturn(50.0);
        when(predictor.getWindowSize()).thenReturn(100);
        when(predictor.getWaitDuration()).thenReturn(1000);
        
        // When
        CircuitBreakerConfig config = configManager.getUpdatedConfig(emptyMetrics);
        
        // Then
        assertThat(config).isNotNull();
        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getSlidingWindowSize()).isEqualTo(100);
    }
    
    @Test
    @DisplayName("Should handle extreme predictor values")
    void shouldHandleExtremePredictorValues() {
        // Given - extreme values
        double extremeFailureRate = 99.9;
        int extremeWindowSize = 10000;
        int extremeWaitDuration = 300000; // 5 minutes in ms
        
        when(predictor.getFailureThreshold()).thenReturn(extremeFailureRate);
        when(predictor.getWindowSize()).thenReturn(extremeWindowSize);
        when(predictor.getWaitDuration()).thenReturn(extremeWaitDuration);
        
        // When
        CircuitBreakerConfig config = configManager.getUpdatedConfig(metrics);
        
        // Then
        assertThat(config.getFailureRateThreshold()).isEqualTo((float)extremeFailureRate);
        assertThat(config.getSlidingWindowSize()).isEqualTo(extremeWindowSize);
    }
    
    @Test
    @DisplayName("Should set slowCallRateThreshold to fixed value")
    void shouldSetSlowCallRateThresholdToFixedValue() {
        // Given
        when(predictor.getFailureThreshold()).thenReturn(50.0);
        when(predictor.getWindowSize()).thenReturn(100);
        when(predictor.getWaitDuration()).thenReturn(1000);
        
        // When
        CircuitBreakerConfig config = configManager.getUpdatedConfig(metrics);
        
        // Then
        // The slowCallRateThreshold should be fixed at 50.0f
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(50.0f);
    }
    
    @Test
    @DisplayName("Should update circuit breaker config successfully")
    void shouldUpdateCircuitBreakerConfigSuccessfully() {
        // Given
        String breakerName = "testBreaker";
        CircuitBreakerConfig currentConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(50)
                .failureRateThreshold(40.0f)
                .waitIntervalFunctionInOpenState(IntervalFunction.of(Duration.ofMillis(500)))
                .build();
        
        // Setup predictor with significantly different values
        when(predictor.getFailureThreshold()).thenReturn(60.0);  // 50% change
        when(predictor.getWindowSize()).thenReturn(100);        // 100% change
        when(predictor.getWaitDuration()).thenReturn(2000);    // 300% change
        
        // Setup circuit breaker registry and circuit breaker
        when(circuitBreakerRegistry.circuitBreaker(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.getCircuitBreakerConfig()).thenReturn(currentConfig);

        
        // When
        boolean updated = configManager.updateCircuitBreakerConfig(breakerName, metrics);
        
        // Then
        assertThat(updated).isTrue();
        verify(circuitBreakerRegistry).circuitBreaker(breakerName);
        verify(circuitBreaker).getCircuitBreakerConfig();
    }
}
