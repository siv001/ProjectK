package org.projectk.service;

import org.projectk.config.CacheConfig;
import org.projectk.dto.ServiceOneResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ServiceOneClient {
    private final RestTemplate rest;
    private final CacheConfig cacheConfig;

    public ServiceOneClient(RestTemplate rest, CacheConfig cacheConfig) {
        this.rest = rest;
        this.cacheConfig = cacheConfig;
    }

    /** actually calls the external Service One API */
    public ServiceOneResponse fetchFresh(String id) {
        String url = "https://api.example.com/serviceOne?id=" + id;
        ServiceOneResponse response = new ServiceOneResponse();// rest.getForObject(url, ServiceOneResponse.class);
        response.setData(id);
        response.setTtl(60); // Simulate a TTL of 60 seconds
        // Track the key with its TTL for dynamic refresh scheduling
        if (response != null) {
            // Debug output to verify TTL value
            System.out.println("ServiceOne response for key " + id + " has TTL: " + response.getTtl() + " seconds");
            cacheConfig.trackCacheKey("serviceOneCache", id, response.getTtl());
        }
        
        return response;
    }

    /** cached wrapper around fetchFresh(...) */
    @Cacheable(value = "serviceOneCache", key = "#id")
    public ServiceOneResponse fetchData(String id) {
        return fetchFresh(id);
    }
}
