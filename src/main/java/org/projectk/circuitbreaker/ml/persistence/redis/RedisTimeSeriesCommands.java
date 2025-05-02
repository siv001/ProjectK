package org.projectk.circuitbreaker.ml.persistence.redis;

/**
 * Utility class to generate Redis TimeSeries commands.
 * This simplifies creating byte arrays for Redis command arguments.
 */
public class RedisTimeSeriesCommands {

    /**
     * Create a command to create a new time series with optional retention
     * 
     * @param key The key name for the time series
     * @return Byte array of command arguments
     */
    public static byte[] getCreateCommand(String key) {
        return key.getBytes();
    }
    
    /**
     * Create a command to create a new time series with retention
     * 
     * @param key The key name for the time series
     * @param retentionMs Retention period in milliseconds
     * @return Byte array of command arguments
     */
    public static byte[] getCreateWithRetentionCommand(String key, long retentionMs) {
        String cmd = String.format("%s RETENTION %d", key, retentionMs);
        return cmd.getBytes();
    }
    
    /**
     * Create a command to add a data point to a time series
     * 
     * @param key The key name for the time series
     * @param timestamp The timestamp in milliseconds
     * @param value The value to add
     * @return Byte array of command arguments
     */
    public static byte[] getAddCommand(String key, long timestamp, double value) {
        String cmd = String.format("%s %d %.10f", key, timestamp, value);
        return cmd.getBytes();
    }
    
    /**
     * Create a command to get a range of data points from a time series
     * 
     * @param key The key name for the time series
     * @param fromTimestamp Start timestamp in milliseconds (inclusive)
     * @param toTimestamp End timestamp in milliseconds (inclusive)
     * @return Byte array of command arguments
     */
    public static byte[] getRangeCommand(String key, long fromTimestamp, long toTimestamp) {
        String cmd = String.format("%s %d %d", key, fromTimestamp, toTimestamp);
        return cmd.getBytes();
    }
}
