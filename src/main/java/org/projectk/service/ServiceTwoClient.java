package org.projectk.service;

import org.projectk.dto.ServiceTwoResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ServiceTwoClient {
    private final RestTemplate rest;

    public ServiceTwoClient(RestTemplate rest) {
        this.rest = rest;
    }

    public ServiceTwoResponse fetchFresh(String id) {
        String url = "https://api.example.com/serviceTwo?id=" + id;
        return rest.getForObject(url, ServiceTwoResponse.class);
    }

    @Cacheable(value = "serviceTwoCache", key = "#id")
    public ServiceTwoResponse fetchData(String id) {
        return fetchFresh(id);
    }
}
