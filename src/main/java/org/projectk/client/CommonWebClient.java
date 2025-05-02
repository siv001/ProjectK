package org.projectk.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Common WebClient implementation that provides circuit breaking
 * and standardized response handling for all web service calls.
 */
@Slf4j
@Component
public class CommonWebClient {

    private static final String CIRCUIT_BREAKER_NAME = "commonWebClient";
    private final WebClient.Builder webClientBuilder;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    public CommonWebClient(WebClient.Builder webClientBuilder, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.webClientBuilder = webClientBuilder;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    /**
     * Call a service with circuit breaker protection
     *
     * @param request Request details including URL, HTTP method, and optional body
     * @param responseType Expected response type
     * @return CompletableFuture with the service response
     */
    public <T, R> CompletableFuture<ClientResponse<R>> callService(ClientRequest<T> request, Class<R> responseType) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
        
        log.debug("Calling service: {} {} with circuit breaker: {}", 
                request.method(), request.url(), CIRCUIT_BREAKER_NAME);
        
        Mono<R> responseMono = createRequest(request, responseType);
        
        return responseMono
            .transform(CircuitBreakerOperator.of(circuitBreaker))
            .map(response -> {
                log.debug("Service call successful: {}", request.url());
                return new ClientResponse<>(response, null, 200);
            })
            .onErrorResume(error -> {
                log.error("Error calling service {}: {}", request.url(), error.getMessage());
                int statusCode = extractStatusCode(error);
                return Mono.just(new ClientResponse<>(null, error.getMessage(), statusCode));
            })
            .toFuture();
    }
    
    /**
     * Create a WebClient request based on the client request details
     */
    <T, R> Mono<R> createRequest(ClientRequest<T> request, Class<R> responseType) {
        WebClient webClient = webClientBuilder.build();
        
        if (request.method() == HttpMethod.GET) {
            return webClient
                .get()
                .uri(request.url())
                .retrieve()
                .bodyToMono(responseType);
        } else {
            WebClient.RequestBodyUriSpec requestSpec = webClient.method(request.method());
            WebClient.RequestHeadersSpec<?> headersSpec;
            
            if (request.requestBody() != null) {
                headersSpec = requestSpec
                    .uri(request.url())
                    .bodyValue(request.requestBody());
            } else {
                headersSpec = requestSpec
                    .uri(request.url());
            }
            
            return headersSpec
                .retrieve()
                .bodyToMono(responseType);
        }
    }
    
    /**
     * Extract HTTP status code from an exception if possible
     */
    private int extractStatusCode(Throwable error) {
        if (error instanceof WebClientResponseException) {
            return ((WebClientResponseException) error).getStatusCode().value();
        }
        return 500; // Default to 500 for non-HTTP errors
    }
}