package org.projectk.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.projectk.dto.WeatherPointResponse;
import org.projectk.service.WeatherService;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherControllerTest {

    @Mock
    private WeatherService weatherService;

    @InjectMocks
    private WeatherController weatherController;

    private WeatherPointResponse mockResponse;

    @BeforeEach
    void setUp() {
        // Set up a mock response
        mockResponse = new WeatherPointResponse();
        
        // Create geometry
        WeatherPointResponse.Geometry geometry = new WeatherPointResponse.Geometry();
        geometry.setType("Point");
        geometry.setCoordinates(new double[]{-97.0892, 39.7456});
        mockResponse.setGeometry(geometry);
        
        // Create properties
        WeatherPointResponse.Properties properties = new WeatherPointResponse.Properties();
        properties.setGridId("TOP");
        properties.setGridX(31);
        properties.setGridY(80);
        properties.setForecast("https://api.weather.gov/gridpoints/TOP/31,80/forecast");
        properties.setForecastHourly("https://api.weather.gov/gridpoints/TOP/31,80/forecast/hourly");
        properties.setForecastGridData("https://api.weather.gov/gridpoints/TOP/31,80");
        properties.setObservationStations("https://api.weather.gov/gridpoints/TOP/31,80/stations");
        properties.setTimeZone("America/Chicago");
        properties.setRadarStation("KTWX");
        
        mockResponse.setId("https://api.weather.gov/points/39.7456,-97.0892");
        mockResponse.setType("Feature");
        mockResponse.setProperties(properties);
    }

    @Test
    @DisplayName("Should return weather data when coordinates are valid")
    void shouldReturnWeatherDataWhenCoordinatesAreValid() {
        // Given
        double latitude = 39.7456;
        double longitude = -97.0892;
        when(weatherService.getWeatherData(latitude, longitude)).thenReturn(Mono.just(mockResponse));

        // When
        Mono<ResponseEntity<WeatherPointResponse>> result = weatherController.getWeatherByCoordinates(latitude, longitude);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    if (response == null || !response.getStatusCode().is2xxSuccessful()) {
                        return false;
                    }
                    
                    WeatherPointResponse body = response.getBody();
                    if (body == null || body.getProperties() == null) {
                        return false;
                    }
                    
                    return "TOP".equals(body.getProperties().getGridId()) &&
                           body.getProperties().getGridX() == 31 &&
                           body.getProperties().getGridY() == 80;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle errors from weather service")
    void shouldHandleErrorsFromWeatherService() {
        // Given
        double latitude = 39.7456;
        double longitude = -97.0892;
        RuntimeException exception = new RuntimeException("API error");
        when(weatherService.getWeatherData(latitude, longitude)).thenReturn(Mono.error(exception));

        // When
        Mono<ResponseEntity<WeatherPointResponse>> result = weatherController.getWeatherByCoordinates(latitude, longitude);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof RuntimeException && "API error".equals(error.getMessage()))
                .verify();
    }
}
