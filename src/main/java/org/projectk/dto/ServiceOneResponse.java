package org.projectk.dto;

import lombok.Data;

@Data
public class ServiceOneResponse {
    private String data;
    private long   ttl;     // Time to live in seconds
}
