package org.projectk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

/**
 * DTO representing the weather.gov points API response
 * Based on the structure from https://api.weather.gov/points/{latitude},{longitude}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherPointResponse {
    @JsonProperty("@context")
    private Object context;
    
    private String id;
    private String type;
    private Geometry geometry;
    private Properties properties;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geometry {
        private String type;
        private double[] coordinates;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Properties {
        @JsonProperty("@id")
        private String id;
        
        @JsonProperty("@type")
        private String type;
        
        private String cwa;
        private String forecastOffice;
        private String gridId;
        private int gridX;
        private int gridY;
        private String forecast;
        private String forecastHourly;
        private String forecastGridData;
        private String observationStations;
        private RelativeLocation relativeLocation;
        private String forecastZone;
        private String county;
        private String fireWeatherZone;
        private String timeZone;
        private String radarStation;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelativeLocation {
        private String type;
        private Geometry geometry;
        private LocationProperties properties;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocationProperties {
        private String city;
        private String state;
        private Distance distance;
        private Bearing bearing;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Distance {
        private String unitCode;
        private double value;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Bearing {
        private String unitCode;
        private int value;
    }
    
    /**
     * Context element for the GeoJSON response
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContextElement {
        @JsonProperty("@version")
        private String version;
        
        private String wx;
        private String s;
        private String geo;
        private String unit;
        
        @JsonProperty("@vocab")
        private String vocab;
        
        private Map<String, Object> geometry;
        private String city;
        private String state;
        private Map<String, Object> distance;
        private Map<String, Object> bearing;
        private Map<String, Object> value;
        private Map<String, Object> unitCode;
        private Map<String, Object> forecastOffice;
        private Map<String, Object> forecastGridData;
        private Map<String, Object> publicZone;
        private Map<String, Object> county;
    }
}
