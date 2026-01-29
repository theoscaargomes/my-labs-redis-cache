package com.mylabs.resilientcache.client;

import com.mylabs.resilientcache.model.WeatherResponse;

public interface WeatherClient {
    
    /**
     * Busca dados meteorológicos para uma cidade
     * 
     * @param city nome da cidade
     * @return dados meteorológicos
     * @throws Exception se a API falhar
     */
    WeatherResponse getWeather(String city) throws Exception;
    
    /**
     * Nome do provedor da API
     */
    String getProviderName();
}
