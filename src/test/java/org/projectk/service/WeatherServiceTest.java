package org.projectk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.projectk.client.ClientRequest;
import org.projectk.client.ClientResponse;
import org.projectk.client.CommonWebClient;
import org.projectk.client.MLCircuitBreaker;
import org.projectk.dto.WeatherPointResponse;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    private static final double LATITUDE = 39.7456;
    private static final double LONGITUDE = -97.0892;
    private static final String BASE_URL = "https://api.test.gov";
    private static final String FORMATTED_URL = "https://api.test.gov/points/39.7456,-97.0892";

    @Mock
    private CommonWebClient webClient;

    @Mock
    private MLCircuitBreaker mlCircuitBreaker;

    @InjectMocks
    private WeatherService weatherService;

    @Captor
    private ArgumentCaptor<ClientRequest<Void>> requestCaptor;

    @Captor
    private ArgumentCaptor<Supplier<Mono<WeatherPointResponse>>> supplierCaptor;

    private WeatherPointResponse mockResponse;
    private ClientResponse<WeatherPointResponse> clientResponse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(weatherService, "baseUrl", BASE_URL);

        // Create mock response for testing
        mockResponse = new WeatherPointResponse();
        WeatherPointResponse.Properties properties = new WeatherPointResponse.Properties();
        properties.setGridId("TEST123");
        mockResponse.setProperties(properties);

        // Create client response wrapper
        clientResponse = new ClientResponse<>(mockResponse, null, 200);
    }

    @Test
    void getWeatherData_success() {
        // Arrange
        when(mlCircuitBreaker.executeWithML(any())).thenAnswer(inv -> {
            Supplier<Mono<WeatherPointResponse>> supplier = inv.getArgument(0);
            return supplier.get();
        });

        when(webClient.callService(any(), eq(WeatherPointResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(clientResponse));

        // Act
        Mono<WeatherPointResponse> result = weatherService.getWeatherData(LATITUDE, LONGITUDE);

        // Assert
        StepVerifier.create(result)
                .expectNext(mockResponse)
                .verifyComplete();

        // Verify correct URL and HTTP method were used
        verify(webClient).callService(requestCaptor.capture(), eq(WeatherPointResponse.class));
        ClientRequest<Void> capturedRequest = requestCaptor.getValue();
        assertEquals(FORMATTED_URL, capturedRequest.url());
        assertEquals(HttpMethod.GET, capturedRequest.method());
        assertNull(capturedRequest.requestBody());

        // Verify ML circuit breaker was used
        verify(mlCircuitBreaker).executeWithML(any());
    }

    @Test
    void getWeatherData_error() {
        // Arrange
        when(mlCircuitBreaker.executeWithML(any())).thenAnswer(inv -> {
            Supplier<Mono<WeatherPointResponse>> supplier = inv.getArgument(0);
            return supplier.get();
        });

        when(webClient.callService(any(), eq(WeatherPointResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        new ClientResponse<>(null, "Test error", 500)
                ));

        // Act
        Mono<WeatherPointResponse> result = weatherService.getWeatherData(LATITUDE, LONGITUDE);

        // Assert
        StepVerifier.create(result)
                .expectError(NullPointerException.class)
                .verify();

        // Verify ML circuit breaker was used
        verify(mlCircuitBreaker).executeWithML(any());
    }

    @Test
    void getWeatherDataWithoutML_success() throws Exception {
        // Arrange
        when(webClient.callService(any(), eq(WeatherPointResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(clientResponse));

        // Act
        CompletableFuture<ClientResponse<WeatherPointResponse>> future = 
                weatherService.getWeatherDataWithoutML(LATITUDE, LONGITUDE);
        ClientResponse<WeatherPointResponse> result = future.get();

        // Assert
        assertNotNull(result);
        assertEquals(mockResponse, result.response());
        assertNull(result.error());
        assertEquals(200, result.statusCode());

        // Verify correct URL and HTTP method
        verify(webClient).callService(requestCaptor.capture(), eq(WeatherPointResponse.class));
        ClientRequest<Void> capturedRequest = requestCaptor.getValue();
        assertEquals(FORMATTED_URL, capturedRequest.url());
        assertEquals(HttpMethod.GET, capturedRequest.method());
        assertNull(capturedRequest.requestBody());

        // Verify ML circuit breaker was NOT used
        verifyNoInteractions(mlCircuitBreaker);
    }

    @Test
    void getWeatherDataWithoutML_error() throws Exception {
        // Arrange
        ClientResponse<WeatherPointResponse> errorResponse = 
                new ClientResponse<>(null, "Service unavailable", 500);
        
        when(webClient.callService(any(), eq(WeatherPointResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(errorResponse));

        // Act
        CompletableFuture<ClientResponse<WeatherPointResponse>> future = 
                weatherService.getWeatherDataWithoutML(LATITUDE, LONGITUDE);
        ClientResponse<WeatherPointResponse> result = future.get();

        // Assert
        assertNotNull(result);
        assertNull(result.response());
        assertEquals("Service unavailable", result.error());
        assertEquals(500, result.statusCode());
    }

    @Test
    void getWeatherData_circuitBreakerError() {
        // Arrange
        RuntimeException testError = new RuntimeException("Circuit breaker error");
        
        when(mlCircuitBreaker.executeWithML(any())).thenReturn(Mono.error(testError));

        // Act
        Mono<WeatherPointResponse> result = weatherService.getWeatherData(LATITUDE, LONGITUDE);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(error -> 
                    error instanceof RuntimeException && 
                    "Circuit breaker error".equals(error.getMessage()))
                .verify();

        // Verify ML circuit breaker was used but webClient was not
        verify(mlCircuitBreaker).executeWithML(any());
        verifyNoInteractions(webClient);
    }
}
