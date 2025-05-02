package org.projectk.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CircuitBreakerConfiguration {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)                // Percentage of failures to trigger circuit breaker
            .waitDurationInOpenState(Duration.ofSeconds(10))  // Time circuit stays open before half-open
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)                   // Number of calls to evaluate failure rate
            .minimumNumberOfCalls(5)                // Minimum calls before calculating failure rate
            .permittedNumberOfCallsInHalfOpenState(3)  // Number of test calls in half-open state
            .automaticTransitionFromOpenToHalfOpenEnabled(true)  // Auto transition to half-open
            .build();

        return CircuitBreakerRegistry.of(circuitBreakerConfig);
    }

    @Bean
    public TimeLimiterConfig timeLimiterConfig() {
        return TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(5))  // Timeout for each call
            .cancelRunningFuture(true)              // Cancel running future on timeout
            .build();
    }
}
