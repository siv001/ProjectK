package org.projectk.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MLCircuitBreakerTest {

    private MLCircuitBreaker mlCircuitBreaker;

    @Mock
    private org.projectk.circuitbreaker.ml.MLCircuitBreaker mockDelegate;

    @BeforeEach
    void setUp() {
        // Create the instance with the mock delegate
        mlCircuitBreaker = new MLCircuitBreaker(mockDelegate);
    }

    @Test
    @DisplayName("Should delegate executeWithML to the refactored implementation")
    void shouldDelegateExecuteWithML() {
        // Given
        Supplier<Mono<String>> operation = () -> Mono.just("test result");
        Mono<String> expectedResult = Mono.just("delegated result");
        
        // Configure the mock delegate to return our expected result
        when(mockDelegate.<String>executeWithML(any())).thenReturn(expectedResult);
        
        // When
        Mono<String> result = mlCircuitBreaker.executeWithML(operation);
        
        // Then
        StepVerifier.create(result)
            .expectNext("delegated result")
            .verifyComplete();
        
        // Verify delegation happened with the correct operation
        ArgumentCaptor<Supplier<Mono<String>>> operationCaptor = 
                ArgumentCaptor.forClass((Class<Supplier<Mono<String>>>) (Class<?>) Supplier.class);
        verify(mockDelegate).executeWithML(operationCaptor.capture());
        
        // Verify the captured operation is the same one we passed
        Supplier<Mono<String>> capturedOperation = operationCaptor.getValue();
        assertThat(capturedOperation).isEqualTo(operation);
    }

    @Test
    @DisplayName("Should propagate errors from the delegate")
    void shouldPropagateErrorsFromDelegate() {
        // Given
        Supplier<Mono<String>> operation = () -> Mono.just("test result");
        RuntimeException expectedException = new RuntimeException("expected test exception");
        Mono<String> errorMono = Mono.error(expectedException);
        
        // Configure the mock delegate to return an error
        when(mockDelegate.<String>executeWithML(any())).thenReturn(errorMono);
        
        // When
        Mono<String> result = mlCircuitBreaker.executeWithML(operation);
        
        // Then
        StepVerifier.create(result)
            .expectErrorMatches(error -> 
                error instanceof RuntimeException && 
                "expected test exception".equals(error.getMessage()))
            .verify();
        
        // Verify delegation occurred
        verify(mockDelegate).executeWithML(any());
    }
}
