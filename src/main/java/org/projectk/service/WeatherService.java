package org.projectk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.projectk.client.ClientRequest;
import org.projectk.client.ClientResponse;
import org.projectk.client.CommonWebClient;
import org.projectk.client.MLCircuitBreaker;
import org.projectk.dto.WeatherPointResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Service for interacting with the weather.gov API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {
    
    private final CommonWebClient webClient;
    private final MLCircuitBreaker mlCircuitBreaker;
    
    @Value("${weather.api.base-url:https://api.weather.gov}")
    private String baseUrl;
    
    /**
     * Get weather details for a specific latitude and longitude using ML-enhanced circuit breaker
     *
     * @param latitude The latitude coordinate
     * @param longitude The longitude coordinate
     * @return Weather data for the specified coordinates
     */
    public Mono<WeatherPointResponse> getWeatherData(double latitude, double longitude) {
        String url = String.format("%s/points/%s,%s", baseUrl, latitude, longitude);
        
        return mlCircuitBreaker.executeWithML(() -> 
            Mono.fromFuture(callWeatherApi(url))
                .map(clientResponse -> clientResponse.response())
        );
    }
    
    /**
     * Get weather details without ML circuit breaker
     * 
     * @param latitude The latitude coordinate
     * @param longitude The longitude coordinate
     * @return Weather data for the specified coordinates
     */
    public CompletableFuture<ClientResponse<WeatherPointResponse>> getWeatherDataWithoutML(double latitude, double longitude) {
        String url = String.format("%s/points/%s,%s", baseUrl, latitude, longitude);
        return callWeatherApi(url);
    }
    
    private CompletableFuture<ClientResponse<WeatherPointResponse>> callWeatherApi(String url) {
        ClientRequest<Void> request = new ClientRequest<>(
            url, 
            HttpMethod.GET, 
            null
        );
        
        return webClient.callService(request, WeatherPointResponse.class);
    }
}
