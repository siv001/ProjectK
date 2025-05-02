package org.projectk.circuitbreaker.ml;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Integration test for MLCircuitBreaker focusing on resilience of the circuit breaker
 * under component failure conditions.
 */
public class MLCircuitBreakerIntegrationTest {

    /**
     * This test verifies that operations continue to function successfully
     * even when a critical component like metrics collection fails.
     * 
     * The success criteria is simply that the operation completes normally,
     * which demonstrates that our error handling is working correctly.
     */
    @Test
    @DisplayName("Circuit breaker continues to operate when components fail")
    void circuitBreakerContinuesOperationDespiteFailures() {
        // ARRANGE - Setup a circuit breaker with a failing metrics collector
        
        // Create MLCircuitBreaker and its dependencies
        MLCircuitBreaker circuitBreaker = new MLCircuitBreaker();
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        // Configure a metrics collector that always fails
        MetricsCollector failingCollector = mock(MetricsCollector.class);
        when(failingCollector.getCurrentMetrics())
            .thenThrow(new RuntimeException("Simulated failure in metrics collection"));
        when(failingCollector.createEmptySnapshot())
            .thenReturn(new MetricSnapshot(Collections.emptyList()));
        
        // Wire up the dependencies
        ReflectionTestUtils.setField(circuitBreaker, "name", "testBreaker");
        ReflectionTestUtils.setField(circuitBreaker, "circuitBreakerRegistry", cbRegistry);
        ReflectionTestUtils.setField(circuitBreaker, "meterRegistry", meterRegistry);
        ReflectionTestUtils.setField(circuitBreaker, "metricsCollector", failingCollector);
        
        // Set up other required components with basic implementations
        ThresholdPredictor predictor = mock(ThresholdPredictor.class);
        when(predictor.getOptimalWindowSize()).thenReturn(100);
        when(predictor.getCurrentThreshold()).thenReturn(50.0);
        when(predictor.getOptimalWaitDuration()).thenReturn(Duration.ofSeconds(30));
        ReflectionTestUtils.setField(circuitBreaker, "predictor", predictor);
        
        AnomalyDetector detector = mock(AnomalyDetector.class);
        when(detector.isAnomaly(any())).thenReturn(false);
        ReflectionTestUtils.setField(circuitBreaker, "anomalyDetector", detector);
        
        // Initialize the circuit breaker
        circuitBreaker.init();
        
        // ACT - Execute an operation through the circuit breaker
        
        String expectedResult = "Operation completed successfully";
        Mono<String> result = circuitBreaker.executeWithML(() -> Mono.just(expectedResult));
        
        // ASSERT - Verify operation succeeds despite component failures
        
        // If the operation completes normally with the expected result,
        // it demonstrates that our error handling is working correctly
        StepVerifier.create(result)
            .expectNext(expectedResult)
            .verifyComplete();
    }
}
