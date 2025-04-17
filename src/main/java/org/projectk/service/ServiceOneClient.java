package org.projectk.service;

import org.projectk.dto.ServiceOneResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ServiceOneClient {
    private final RestTemplate rest;

    public ServiceOneClient(RestTemplate rest) {
        this.rest = rest;
    }

    /** actually calls the external Service One API */
    public ServiceOneResponse fetchFresh(String id) {
        String url = "https://api.example.com/serviceOne?id=" + id;
        return rest.getForObject(url, ServiceOneResponse.class);
    }

    /** cached wrapper around fetchFresh(...) */
    @Cacheable(value = "serviceOneCache", key = "#id")
    public ServiceOneResponse fetchData(String id) {
        return fetchFresh(id);
    }
}
