package com.mylabs.resilientcache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mylabs.resilientcache.model.WeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String CACHE_PREFIX = "weather:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    
    /**
     * Salva dados meteorológicos no cache com TTL
     */
    public void save(String city, WeatherResponse data) {
        try {
            String key = buildKey(city);
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, DEFAULT_TTL);
            log.info("[CACHE] Saved data for city: {} (TTL: {})", city, DEFAULT_TTL);
        } catch (Exception e) {
            log.error("[CACHE] Error saving data for city: {}", city, e);
        }
    }
    
    /**
     * Busca dados do cache (apenas dados frescos, dentro do TTL)
     */
    public Optional<WeatherResponse> get(String city) {
        try {
            String key = buildKey(city);
            String json = redisTemplate.opsForValue().get(key);
            
            if (json != null) {
                WeatherResponse data = objectMapper.readValue(json, WeatherResponse.class);
                data.setFromCache(true);
                data.setStaleData(false);
                log.info("[CACHE HIT] Fresh data for city: {}", city);
                return Optional.of(data);
            }
            
            log.info("[CACHE MISS] No fresh data for city: {}", city);
            return Optional.empty();
        } catch (Exception e) {
            log.error("[CACHE] Error reading data for city: {}", city, e);
            return Optional.empty();
        }
    }
    
    /**
     * Busca dados do cache mesmo se expirados (stale data)
     * Usado como último recurso quando todas as APIs falham
     */
    public Optional<WeatherResponse> getStale(String city) {
        try {
            String key = buildKey(city);
            
            // Tenta buscar sem considerar TTL (dados expirados)
            String json = redisTemplate.opsForValue().get(key);
            
            if (json != null) {
                WeatherResponse data = objectMapper.readValue(json, WeatherResponse.class);
                data.setFromCache(true);
                data.setStaleData(true);
                log.warn("[CACHE STALE] Returning expired data for city: {}", city);
                return Optional.of(data);
            }
            
            log.warn("[CACHE EMPTY] No stale data available for city: {}", city);
            return Optional.empty();
        } catch (Exception e) {
            log.error("[CACHE] Error reading stale data for city: {}", city, e);
            return Optional.empty();
        }
    }
    
    /**
     * Invalida cache de uma cidade
     */
    public void invalidate(String city) {
        try {
            String key = buildKey(city);
            redisTemplate.delete(key);
            log.info("[CACHE] Invalidated data for city: {}", city);
        } catch (Exception e) {
            log.error("[CACHE] Error invalidating data for city: {}", city, e);
        }
    }
    
    private String buildKey(String city) {
        return CACHE_PREFIX + city.toLowerCase();
    }
}
