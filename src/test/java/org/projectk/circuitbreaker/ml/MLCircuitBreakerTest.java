package org.projectk.circuitbreaker.ml;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.Metrics;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MLCircuitBreakerTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    
    @Mock
    private CircuitBreaker circuitBreaker;
    
    @Mock
    private Metrics circuitBreakerMetrics;
    
    private MeterRegistry meterRegistry;
    
    private MLCircuitBreaker mlCircuitBreaker;
    
    @BeforeEach
    void setUp() {
        // Real meter registry
        meterRegistry = new SimpleMeterRegistry();
        
        // Setup circuit breaker with lenient stubs to avoid unnecessary stubbing errors
        lenient().when(circuitBreakerRegistry.circuitBreaker(anyString(), any(CircuitBreakerConfig.class)))
            .thenReturn(circuitBreaker);
        lenient().when(circuitBreaker.getMetrics()).thenReturn(circuitBreakerMetrics);
        lenient().when(circuitBreakerMetrics.getNumberOfBufferedCalls()).thenReturn(1);
        
        // Create the real instance for testing
        mlCircuitBreaker = new MLCircuitBreaker();
        
        // Set fields using reflection to simulate dependency injection
        ReflectionTestUtils.setField(mlCircuitBreaker, "name", "testBreaker");
        ReflectionTestUtils.setField(mlCircuitBreaker, "meterRegistry", meterRegistry);
        ReflectionTestUtils.setField(mlCircuitBreaker, "circuitBreakerRegistry", circuitBreakerRegistry);
        
        // Manually call init which would normally be called by @PostConstruct
        mlCircuitBreaker.init();
    }
    
    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {
        
        @Test
        @DisplayName("Should initialize with circuit breaker registry")
        void shouldInitializeWithCircuitBreakerRegistry() {
            // Verify that the circuit breaker registry was called during initialization
            verify(circuitBreakerRegistry).circuitBreaker(anyString(), any(CircuitBreakerConfig.class));
        }
    }
    
    @Nested
    @DisplayName("Execute With ML Tests")
    class ExecuteWithMLTests {
        
        @Test
        @DisplayName("Should execute operation successfully")
        void shouldExecuteOperationSuccessfully() {
            // Given
            Supplier<Mono<String>> operation = () -> Mono.just("test result");
            
            // Make the circuit breaker decorator work
            when(circuitBreaker.decorateSupplier(any())).thenAnswer(invocation -> {
                Supplier<?> original = invocation.getArgument(0);
                return (Supplier<Object>) () -> original.get();
            });
            
            // When
            Mono<String> result = mlCircuitBreaker.executeWithML(operation);
            
            // Then
            StepVerifier.create(result)
                .expectNext("test result")
                .verifyComplete();
            
            // Verify the circuit breaker was used
            verify(circuitBreaker).decorateSupplier(any());
        }
        
        @Test
        @DisplayName("Should handle operation failures")
        void shouldHandleOperationFailures() {
            // Given
            RuntimeException testException = new RuntimeException("test failure");
            Supplier<Mono<String>> operation = () -> Mono.error(testException);
            
            // Make the circuit breaker decorator work and propagate the error
            when(circuitBreaker.decorateSupplier(any())).thenAnswer(invocation -> {
                Supplier<?> original = invocation.getArgument(0);
                return (Supplier<Object>) () -> {
                    try {
                        return original.get();
                    } catch (Exception e) {
                        throw e;
                    }
                };
            });
            
            // When
            Mono<String> result = mlCircuitBreaker.executeWithML(operation);
            
            // Then
            StepVerifier.create(result)
                .expectErrorMatches(error -> 
                    error instanceof RuntimeException && 
                    "test failure".equals(error.getMessage()))
                .verify();
            
            // Verify the circuit breaker was used
            verify(circuitBreaker).decorateSupplier(any());
        }
    }
}
