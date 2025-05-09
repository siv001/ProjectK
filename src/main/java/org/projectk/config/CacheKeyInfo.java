package org.projectk.config;

import java.time.Instant;

public class CacheKeyInfo {
    private final String cacheName;
    private final String key;
    private Instant nextRefreshTime;
    private Instant lastRefreshTime;
    private Instant originalScheduleTime; // Track the original TTL-based schedule
    private long ttlSeconds;
    private long pollFrequency;


    public CacheKeyInfo(String cacheName, String key, Instant nextRefreshTime, Instant lastRefreshTime, long ttlSeconds, long pollFrequency) {
        this.cacheName = cacheName;
        this.key = key;
        this.nextRefreshTime = nextRefreshTime;
        this.lastRefreshTime = lastRefreshTime;
        this.ttlSeconds = ttlSeconds;
        this.pollFrequency = pollFrequency;
        this.originalScheduleTime = nextRefreshTime; // Initialize with the first scheduled time
    }

    public String getCacheName() {
        return cacheName;
    }

    public String getKey() {
        return key;
    }

    public Instant getNextRefreshTime() {
        return nextRefreshTime;
    }

    public void setNextRefreshTime(Instant nextRefreshTime) {
        this.nextRefreshTime = nextRefreshTime;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }


    public long getPollFrequency() {
        return pollFrequency;
    }

    public void setPollFrequency(long pollFrequency) {
        this.pollFrequency = pollFrequency;
    }

    public Instant getLastRefreshTime() {
        return lastRefreshTime;
    }

    public void setLastRefreshTime(Instant lastRefreshTime) {
        this.lastRefreshTime = lastRefreshTime;
    }
    
    public Instant getOriginalScheduleTime() {
        return originalScheduleTime;
    }
    
    public void updateOriginalScheduleTime() {
        // Update to the next TTL interval based on the original schedule
        this.originalScheduleTime = this.originalScheduleTime.plusSeconds(ttlSeconds);
    }
}