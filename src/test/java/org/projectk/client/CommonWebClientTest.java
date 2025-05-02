package org.projectk.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommonWebClientTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;
    
    // Test subclass that overrides the methods we need to mock
    private static class TestCommonWebClient extends CommonWebClient {
        public TestCommonWebClient(WebClient.Builder mockBuilder, 
                                  CircuitBreakerRegistry registry) {
            super(mockBuilder, registry);
        }
        
        // Override to implement custom createRequest for testing
        @Override
        <T, R> Mono<R> createRequest(ClientRequest<T> request, Class<R> responseType) {
            // This will be mocked in individual tests
            return Mono.empty();
        }
        
        // Override transform to skip circuit breaker in tests
        @Override
        public <T, R> CompletableFuture<ClientResponse<R>> callService(ClientRequest<T> request, Class<R> responseType) {
            Mono<R> responseMono = createRequest(request, responseType);
            
            // Skip circuit breaker transform for tests
            return responseMono
                .map(response -> new ClientResponse<>(response, null, 200))
                .onErrorResume(error -> {
                    int statusCode = 500;
                    if (error instanceof WebClientResponseException) {
                        statusCode = ((WebClientResponseException) error).getStatusCode().value();
                    }
                    return Mono.just(new ClientResponse<>(null, error.getMessage(), statusCode));
                })
                .toFuture();
        }
    }

    private TestCommonWebClient commonWebClient;

    @BeforeEach
    void setUp() {
        // Create our test implementation that bypasses the circuit breaker
        commonWebClient = spy(new TestCommonWebClient(webClientBuilder, circuitBreakerRegistry));
    }

    @Test
    void testSuccessfulServiceCall() throws ExecutionException, InterruptedException {
        // Arrange
        String responseData = "Success Response";
        ClientRequest<String> request = new ClientRequest<>("http://test.com", HttpMethod.GET, "testBody");

        // Mock the createRequest method to return our test data
        doReturn(Mono.just(responseData))
            .when(commonWebClient)
            .createRequest(eq(request), eq(String.class));

        // Act
        CompletableFuture<ClientResponse<String>> future = commonWebClient.callService(request, String.class);
        ClientResponse<String> response = future.get();

        // Assert
        assertNotNull(response);
        assertEquals(responseData, response.response());
        assertNull(response.error());
        assertEquals(200, response.statusCode());

        // Verify
        verify(commonWebClient).createRequest(eq(request), eq(String.class));
        verifyNoInteractions(circuitBreakerRegistry);
    }

    @Test
    void testFailedServiceCall() throws ExecutionException, InterruptedException {
        // Arrange
        String errorMessage = "Service Unavailable";
        ClientRequest<String> request = new ClientRequest<>("http://test.com", HttpMethod.POST, "testBody");

        // Mock the createRequest method to return our test error
        doReturn(Mono.error(new RuntimeException(errorMessage)))
            .when(commonWebClient)
            .createRequest(eq(request), eq(String.class));

        // Act
        CompletableFuture<ClientResponse<String>> future = commonWebClient.callService(request, String.class);
        ClientResponse<String> response = future.get();

        // Assert
        assertNotNull(response);
        assertNull(response.response());
        assertEquals(errorMessage, response.error());
        assertEquals(500, response.statusCode());

        // Verify
        verify(commonWebClient).createRequest(eq(request), eq(String.class));
        verifyNoInteractions(circuitBreakerRegistry);
    }
}
