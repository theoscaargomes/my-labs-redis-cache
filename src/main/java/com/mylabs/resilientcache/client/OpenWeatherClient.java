package com.mylabs.resilientcache.client;

import com.mylabs.resilientcache.model.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;

@Slf4j
@Component
public class OpenWeatherClient implements WeatherClient {
    
    @Value("${weather.api.primary.fail-rate:0.3}")
    private double failRate;
    
    @Value("${weather.api.primary.delay-ms:100}")
    private int delayMs;
    
    private final Random random = new Random();
    
    @Override
    public WeatherResponse getWeather(String city) throws Exception {
        log.info("[PRIMARY API] Calling OpenWeather for city: {}", city);
        
        // Simula latência da API
        Thread.sleep(delayMs);
        
        // Simula falhas aleatórias (configurável)
        if (random.nextDouble() < failRate) {
            log.error("[PRIMARY API] OpenWeather failed for city: {}", city);
            throw new Exception("OpenWeather API unavailable");
        }
        
        // Simula resposta de sucesso
        WeatherResponse response = WeatherResponse.builder()
                .city(city)
                .temperature(22.5 + random.nextDouble() * 10)
                .description("Partly Cloudy")
                .humidity(65 + random.nextInt(20))
                .windSpeed(15.0 + random.nextDouble() * 10)
                .source("OpenWeather")
                .timestamp(Instant.now())
                .fromCache(false)
                .staleData(false)
                .build();
        
        log.info("[PRIMARY API] OpenWeather success for {}: {}°C", city, response.getTemperature());
        return response;
    }
    
    @Override
    public String getProviderName() {
        return "OpenWeather";
    }
}
