package com.mylabs.resilientcache.controller;

import com.mylabs.resilientcache.model.WeatherResponse;
import com.mylabs.resilientcache.service.WeatherService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {
    
    private final WeatherService weatherService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    /**
     * Endpoint principal - busca dados meteorológicos com fallback chain
     */
    @GetMapping("/{city}")
    public ResponseEntity<WeatherResponse> getWeather(@PathVariable String city) {
        log.info("Received request for city: {}", city);
        WeatherResponse response = weatherService.getWeather(city);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Health check - mostra estado dos circuit breakers
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        
        // Estado do Circuit Breaker da API primária
        var primaryCB = circuitBreakerRegistry.circuitBreaker("primaryWeatherApi");
        health.put("primaryApi", Map.of(
            "state", primaryCB.getState().toString(),
            "failureRate", primaryCB.getMetrics().getFailureRate() + "%",
            "slowCallRate", primaryCB.getMetrics().getSlowCallRate() + "%"
        ));
        
        // Estado do Circuit Breaker da API secundária
        var secondaryCB = circuitBreakerRegistry.circuitBreaker("secondaryWeatherApi");
        health.put("secondaryApi", Map.of(
            "state", secondaryCB.getState().toString(),
            "failureRate", secondaryCB.getMetrics().getFailureRate() + "%",
            "slowCallRate", secondaryCB.getMetrics().getSlowCallRate() + "%"
        ));
        
        return ResponseEntity.ok(health);
    }
}
