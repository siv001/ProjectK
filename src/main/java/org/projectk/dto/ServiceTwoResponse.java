package org.projectk.dto;

import lombok.Data;

@Data
public class ServiceTwoResponse {
    private String data;
    private long   ttl;           // Time to live in seconds
    private long   pollFrequency; // Frequency to poll for changes after TTL expiration in seconds
}