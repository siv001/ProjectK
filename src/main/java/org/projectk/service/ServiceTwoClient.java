package org.projectk.service;

import org.projectk.config.CacheConfig;
import org.projectk.dto.ServiceTwoResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ServiceTwoClient {
    private final RestTemplate rest;
    private final CacheConfig cacheConfig;

    public ServiceTwoClient(RestTemplate rest, CacheConfig cacheConfig) {
        this.rest = rest;
        this.cacheConfig = cacheConfig;
    }

    public ServiceTwoResponse fetchFresh(String id) {
        String url = "https://api.example.com/serviceTwo?id=" + id;
        ServiceTwoResponse response = new ServiceTwoResponse();//rest.getForObject(url, ServiceTwoResponse.class);
        response.setData(id);
        response.setTtl(120); // Simulate a TTL of 120 seconds
        // Track the key with its TTL for dynamic refresh scheduling
        if (response != null) {
            // Debug output to verify TTL value
            System.out.println("ServiceTwo response for key " + id + " has TTL: " + response.getTtl() + " seconds");
            cacheConfig.trackCacheKey("serviceTwoCache", id, response.getTtl());
        }
        
        return response;
    }

    @Cacheable(value = "serviceTwoCache", key = "#id")
    public ServiceTwoResponse fetchData(String id) {
        return fetchFresh(id);
    }
}
