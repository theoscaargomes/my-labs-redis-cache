package com.mylabs.resilientcache.client;

import com.mylabs.resilientcache.model.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;

@Slf4j
@Component
public class WeatherApiClient implements WeatherClient {
    
    @Value("${weather.api.secondary.fail-rate:0.2}")
    private double failRate;
    
    @Value("${weather.api.secondary.delay-ms:150}")
    private int delayMs;
    
    private final Random random = new Random();
    
    @Override
    public WeatherResponse getWeather(String city) throws Exception {
        log.info("[SECONDARY API] Calling WeatherAPI for city: {}", city);
        
        // Simula latência da API
        Thread.sleep(delayMs);
        
        // Simula falhas aleatórias (configurável)
        if (random.nextDouble() < failRate) {
            log.error("[SECONDARY API] WeatherAPI failed for city: {}", city);
            throw new Exception("WeatherAPI unavailable");
        }
        
        // Simula resposta de sucesso
        WeatherResponse response = WeatherResponse.builder()
                .city(city)
                .temperature(21.0 + random.nextDouble() * 12)
                .description("Clear Sky")
                .humidity(60 + random.nextInt(25))
                .windSpeed(12.0 + random.nextDouble() * 8)
                .source("WeatherAPI")
                .timestamp(Instant.now())
                .fromCache(false)
                .staleData(false)
                .build();
        
        log.info("[SECONDARY API] WeatherAPI success for {}: {}°C", city, response.getTemperature());
        return response;
    }
    
    @Override
    public String getProviderName() {
        return "WeatherAPI";
    }
}
