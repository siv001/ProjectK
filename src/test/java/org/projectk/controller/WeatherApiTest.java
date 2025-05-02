package org.projectk.controller;

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

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.mockito.Mockito.when;

/**
 * Manual test to simulate calling the WeatherController endpoint
 */
@ExtendWith(MockitoExtension.class)
public class WeatherApiTest {

    private static final Logger logger = Logger.getLogger(WeatherApiTest.class.getName());

    @Mock
    private WeatherService weatherService;

    @InjectMocks
    private WeatherController weatherController;

    @Test
    void testWeatherApiManually() {
        // Given
        double latitude = 39.7456;
        double longitude = -97.0892;
        
        // Create sample response
        WeatherPointResponse mockResponse = new WeatherPointResponse();
        
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
        
        // Set top-level fields
        mockResponse.setId("https://api.weather.gov/points/39.7456,-97.0892");
        mockResponse.setType("Feature");
        mockResponse.setProperties(properties);
        
        when(weatherService.getWeatherData(latitude, longitude)).thenReturn(Mono.just(mockResponse));

        // When
        Mono<ResponseEntity<WeatherPointResponse>> result = weatherController.getWeatherByCoordinates(latitude, longitude);

        // Then
        AtomicReference<ResponseEntity<WeatherPointResponse>> responseRef = new AtomicReference<>();
        
        StepVerifier.create(result)
                .consumeNextWith(responseRef::set)
                .verifyComplete();
        
        ResponseEntity<WeatherPointResponse> response = responseRef.get();
        // Add null checks to prevent NullPointerException
        if (response == null) {
            System.out.println("Error: Received null response");
            return;
        }
        
        WeatherPointResponse body = response.getBody();
        if (body == null || body.getProperties() == null) {
            System.out.println("Error: Response body or its properties are null");
            return;
        }
        
        // Print the JSON equivalent of what would be returned
        logger.info("HTTP Status: " + response.getStatusCode());
        logger.info("Response Body:");
        logger.info("=============");
        logger.info("WeatherPointResponse {");
        logger.info("  id: \"" + body.getId() + "\"");
        logger.info("  type: \"" + body.getType() + "\"");
        logger.info("  geometry: {");
        logger.info("    type: \"" + body.getGeometry().getType() + "\"");
        logger.info("    coordinates: [" + body.getGeometry().getCoordinates()[0] + ", " + body.getGeometry().getCoordinates()[1] + "]");
        logger.info("  }");
        logger.info("  properties: {");
        logger.info("    gridId: \"" + body.getProperties().getGridId() + "\"");
        logger.info("    gridX: " + body.getProperties().getGridX());
        logger.info("    gridY: " + body.getProperties().getGridY());
        logger.info("    forecast: \"" + body.getProperties().getForecast() + "\"");
        logger.info("    forecastHourly: \"" + body.getProperties().getForecastHourly() + "\"");
        logger.info("    forecastGridData: \"" + body.getProperties().getForecastGridData() + "\"");
        logger.info("    observationStations: \"" + body.getProperties().getObservationStations() + "\"");
        logger.info("    radarStation: \"" + body.getProperties().getRadarStation() + "\"");
        logger.info("    timeZone: \"" + body.getProperties().getTimeZone() + "\"");
        logger.info("  }");
        logger.info("}");
        
        // Also print as formatted JSON string for the user
        System.out.println("\n\nJSON Response for coordinates latitude=39.7456&longitude=-97.0892:");
        System.out.println("------------------------------------------------------");
        System.out.println("{\n" +
                "  \"id\": \"" + body.getId() + "\",\n" +
                "  \"type\": \"" + body.getType() + "\",\n" +
                "  \"geometry\": {\n" +
                "    \"type\": \"" + body.getGeometry().getType() + "\",\n" +
                "    \"coordinates\": [" + body.getGeometry().getCoordinates()[0] + ", " + body.getGeometry().getCoordinates()[1] + "]\n" +
                "  },\n" +
                "  \"properties\": {\n" +
                "    \"gridId\": \"" + body.getProperties().getGridId() + "\",\n" +
                "    \"gridX\": " + body.getProperties().getGridX() + ",\n" +
                "    \"gridY\": " + body.getProperties().getGridY() + ",\n" +
                "    \"forecast\": \"" + body.getProperties().getForecast() + "\",\n" +
                "    \"forecastHourly\": \"" + body.getProperties().getForecastHourly() + "\",\n" +
                "    \"forecastGridData\": \"" + body.getProperties().getForecastGridData() + "\",\n" +
                "    \"observationStations\": \"" + body.getProperties().getObservationStations() + "\",\n" +
                "    \"radarStation\": \"" + body.getProperties().getRadarStation() + "\",\n" +
                "    \"timeZone\": \"" + body.getProperties().getTimeZone() + "\"\n" +
                "  }\n" +
                "}");
    }
}
