package org.projectk.circuitbreaker.ml;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This diagnostic test helps isolate issues with CircuitBreakerConfig creation
 */
public class CircuitBreakerDiagnosticTest {

    @Test
    @DisplayName("Test simple CircuitBreakerConfig creation")
    void testSimpleCircuitBreakerConfig() {
        // Simple config with minimal settings
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(100)
                .failureRateThreshold(50.0f)
                .build();
                
        assertThat(config).isNotNull();
        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
    }
    
    @Test
    @DisplayName("Test CircuitBreakerConfig creation with all settings")
    void testFullCircuitBreakerConfig() {
        // Full config with all settings similar to what we're using
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(100)
                .failureRateThreshold(50.0f)
                .waitIntervalFunctionInOpenState(IntervalFunction.of(Duration.ofMillis(1000)))
                .slowCallRateThreshold(50.0f)
                .minimumNumberOfCalls(10) 
                .permittedNumberOfCallsInHalfOpenState(5)
                .slowCallDurationThreshold(Duration.ofSeconds(1))
                .build();
                
        assertThat(config).isNotNull();
        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(50.0f);
    }
}
