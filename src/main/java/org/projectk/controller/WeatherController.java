package org.projectk.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.projectk.dto.WeatherPointResponse;
import org.projectk.service.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Controller for accessing weather data from weather.gov API
 */
@Slf4j
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    /**
     * Get weather information for a specific location by coordinates
     *
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @return Weather information for the specified location
     */
    @GetMapping("/points")
    public Mono<ResponseEntity<WeatherPointResponse>> getWeatherByCoordinates(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        
        log.info("Getting weather data for coordinates: {}, {}", latitude, longitude);
        
        return weatherService.getWeatherData(latitude, longitude)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("Successfully retrieved weather data"))
                .doOnError(error -> log.error("Error retrieving weather data: {}", error.getMessage()));
    }
}
