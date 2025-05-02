package org.projectk.circuitbreaker.ml.persistence.redis;

/**
 * Represents a single data point in a Redis TimeSeries.
 */
public class TimeSeriesPoint {
    private final long timestamp;
    private final double value;
    
    public TimeSeriesPoint(long timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }
    
    /**
     * Get the timestamp of this data point
     * @return timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Get the value of this data point
     * @return the stored value
     */
    public double getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return String.format("[%d: %.4f]", timestamp, value);
    }
}
