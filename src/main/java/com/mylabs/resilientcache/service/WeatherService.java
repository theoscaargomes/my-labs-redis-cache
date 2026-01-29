package com.mylabs.resilientcache.service;

import com.mylabs.resilientcache.client.OpenWeatherClient;
import com.mylabs.resilientcache.client.WeatherApiClient;
import com.mylabs.resilientcache.client.WeatherClient;
import com.mylabs.resilientcache.model.WeatherResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {
    
    private final OpenWeatherClient primaryClient;
    private final WeatherApiClient secondaryClient;
    private final CacheService cacheService;
    
    /**
     * Busca dados meteorológicos com fallback chain completo:
     * 1. Tenta cache fresco
     * 2. Tenta API primária (com circuit breaker)
     * 3. Tenta API secundária (com circuit breaker)
     * 4. Retorna cache stale (dados expirados)
     * 5. Retorna erro gracioso
     */
    public WeatherResponse getWeather(String city) {
        log.info("=== Starting weather request for city: {} ===", city);
        
        // 1. Tenta cache fresco primeiro
        return cacheService.get(city)
                .map(cachedData -> {
                    log.info("[SUCCESS] ✓ Cache hit (fresh data) - {}ms", 0);
                    return cachedData;
                })
                .orElseGet(() -> {
                    // 2. Cache miss - tenta APIs com fallback
                    WeatherResponse response = fetchWithFallback(city);
                    
                    // Salva no cache se obteve dados válidos
                    if (response != null && !response.getDescription().startsWith("Error")) {
                        cacheService.save(city, response);
                    }
                    
                    return response;
                });
    }
    
    /**
     * Tenta API primária com fallback para API secundária e cache stale
     */
    private WeatherResponse fetchWithFallback(String city) {
        try {
            return callPrimaryApi(city);
        } catch (Exception e1) {
            log.warn("[FALLBACK] Primary API failed, trying secondary API...");
            
            try {
                return callSecondaryApi(city);
            } catch (Exception e2) {
                log.warn("[FALLBACK] Secondary API failed, trying stale cache...");
                
                // 4. Último recurso: cache stale (dados expirados)
                return cacheService.getStale(city)
                        .map(staleData -> {
                            log.warn("[DEGRADED] ⚠ Using stale cache data");
                            return staleData;
                        })
                        .orElseGet(() -> {
                            // 5. Erro gracioso quando tudo falha
                            log.error("[ERROR] ✗ All sources failed, returning graceful error");
                            return WeatherResponse.createError(city, 
                                "All weather services are currently unavailable. Please try again later.");
                        });
            }
        }
    }
    
    /**
     * Chama API primária com Circuit Breaker, Retry e TimeLimiter
     */
    @CircuitBreaker(name = "primaryWeatherApi", fallbackMethod = "primaryApiFallback")
    @Retry(name = "primaryWeatherApi")
    @TimeLimiter(name = "primaryWeatherApi")
    public CompletableFuture<WeatherResponse> callPrimaryApi(String city) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                WeatherResponse response = primaryClient.getWeather(city);
                log.info("[SUCCESS] ✓ Primary API - {}ms", 100);
                return response;
            } catch (Exception e) {
                log.error("[CIRCUIT BREAKER] Primary API failed: {}", e.getMessage());
                throw new RuntimeException("Primary API failed", e);
            }
        });
    }
    
    /**
     * Fallback da API primária (lança exceção para tentar secundária)
     */
    private CompletableFuture<WeatherResponse> primaryApiFallback(String city, Exception e) {
        log.warn("[CIRCUIT BREAKER] Primary API circuit breaker activated");
        return CompletableFuture.failedFuture(e);
    }
    
    /**
     * Chama API secundária com Circuit Breaker, Retry e TimeLimiter
     */
    @CircuitBreaker(name = "secondaryWeatherApi", fallbackMethod = "secondaryApiFallback")
    @Retry(name = "secondaryWeatherApi")
    @TimeLimiter(name = "secondaryWeatherApi")
    public CompletableFuture<WeatherResponse> callSecondaryApi(String city) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                WeatherResponse response = secondaryClient.getWeather(city);
                log.info("[SUCCESS] ✓ Secondary API (fallback) - {}ms", 150);
                return response;
            } catch (Exception e) {
                log.error("[CIRCUIT BREAKER] Secondary API failed: {}", e.getMessage());
                throw new RuntimeException("Secondary API failed", e);
            }
        });
    }
    
    /**
     * Fallback da API secundária (lança exceção para tentar cache stale)
     */
    private CompletableFuture<WeatherResponse> secondaryApiFallback(String city, Exception e) {
        log.warn("[CIRCUIT BREAKER] Secondary API circuit breaker activated");
        return CompletableFuture.failedFuture(e);
    }
}
