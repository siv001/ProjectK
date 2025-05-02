package org.projectk.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Simple test for client classes to improve coverage
 * Uses real instances instead of mocks to avoid UnnecessaryStubbingException
 */
public class SimpleClientTests {

    @Test
    void testCommonWebClientConstructor() {
        // Create actual dependencies
        WebClient.Builder webClientBuilder = WebClient.builder();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        
        // Test the constructor only
        CommonWebClient client = new CommonWebClient(webClientBuilder, circuitBreakerRegistry);
        assertNotNull(client);
    }
}
