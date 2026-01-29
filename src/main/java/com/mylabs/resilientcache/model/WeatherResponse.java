package com.mylabs.resilientcache.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherResponse implements Serializable {
    
    private String city;
    private Double temperature;
    private String description;
    private Integer humidity;
    private Double windSpeed;
    private String source;
    private Instant timestamp;
    private Boolean fromCache;
    private Boolean staleData;
    
    public static WeatherResponse createError(String city, String errorMessage) {
        return WeatherResponse.builder()
                .city(city)
                .description("Error: " + errorMessage)
                .timestamp(Instant.now())
                .fromCache(false)
                .staleData(false)
                .build();
    }
}
