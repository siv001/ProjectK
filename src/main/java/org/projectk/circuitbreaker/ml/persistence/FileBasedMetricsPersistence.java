package org.projectk.circuitbreaker.ml.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.projectk.circuitbreaker.ml.MetricSnapshot;
import org.projectk.circuitbreaker.ml.ServiceMetric;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Simple file-based implementation of MetricsPersistenceProvider.
 * This implementation stores circuit breaker metrics in JSON files,
 * which doesn't require any additional infrastructure dependencies.
 */
@Slf4j
@Component
@Profile({"default", "file-metrics"}) // Active by default
public class FileBasedMetricsPersistence implements MetricsPersistenceProvider {

    private final Map<String, Queue<PersistedMetricData>> metricsCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    
    @Value("${metrics.file.base-path:./metrics-data}")
    private String basePath;
    
    @Value("${metrics.file.enabled:true}")
    private boolean enabled;
    
    @Value("${metrics.file.flush-interval-ms:60000}")
    private long flushIntervalMs;
    
    @Value("${metrics.file.retention-days:30}")
    private int retentionDays;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("File-based metrics persistence is disabled");
            return;
        }

        try {
            // Create base directory if it doesn't exist
            Path baseDir = Paths.get(basePath);
            if (!Files.exists(baseDir)) {
                Files.createDirectories(baseDir);
            }
            log.info("File-based metrics persistence initialized with path: {}", basePath);
        } catch (IOException e) {
            log.error("Failed to initialize file-based metrics persistence: {}", e.getMessage(), e);
            enabled = false;
        }
    }

    @Override
    public void storeMetrics(MetricSnapshot snapshot, String circuitBreakerName) {
        if (!enabled || snapshot == null) {
            return;
        }

        // Create data object for persistence
        PersistedMetricData data = new PersistedMetricData();
        data.setTimestamp(LocalDateTime.now());
        data.setCircuitBreakerName(circuitBreakerName);
        data.setErrorRate(snapshot.getErrorRate());
        data.setLatency(snapshot.getP95Latency());
        data.setSystemLoad(snapshot.getSystemLoad());
        data.setCallVolume(snapshot.getMetrics().size());

        // Add to in-memory cache
        metricsCache.computeIfAbsent(circuitBreakerName, k -> new ConcurrentLinkedQueue<>())
                   .add(data);
        
        log.debug("Added metrics to file persistence cache for {}: error rate={}, latency={}", 
                 circuitBreakerName, data.getErrorRate(), data.getLatency());
    }

    @Override
    public List<MetricSnapshot> loadHistoricalMetrics(String circuitBreakerName, Duration lookbackPeriod) {
        if (!enabled) {
            return Collections.emptyList();
        }

        try {
            Path circuitBreakerDir = Paths.get(basePath, sanitizeFileName(circuitBreakerName));
            if (!Files.exists(circuitBreakerDir)) {
                return Collections.emptyList();
            }

            LocalDateTime cutoffTime = LocalDateTime.now().minus(lookbackPeriod);
            List<PersistedMetricData> metrics = new ArrayList<>();

            // Read metrics from files
            Files.list(circuitBreakerDir)
                 .filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".json"))
                 .forEach(path -> {
                     try {
                         PersistedMetricData[] fileMetrics = objectMapper.readValue(path.toFile(), PersistedMetricData[].class);
                         metrics.addAll(Arrays.asList(fileMetrics));
                     } catch (IOException e) {
                         log.error("Error reading metrics file {}: {}", path, e.getMessage());
                     }
                 });

            // Add cached metrics that haven't been flushed yet
            Queue<PersistedMetricData> cachedMetrics = metricsCache.get(circuitBreakerName);
            if (cachedMetrics != null) {
                metrics.addAll(cachedMetrics);
            }

            // Filter by lookback period, sort by timestamp, and convert to MetricSnapshot
            return metrics.stream()
                    .filter(m -> m.getTimestamp().isAfter(cutoffTime))
                    .sorted(Comparator.comparing(PersistedMetricData::getTimestamp))
                    .map(this::convertToMetricSnapshot)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error loading historical metrics: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Scheduled(fixedRateString = "${metrics.file.flush-interval-ms:60000}")
    public void flushMetricsToFiles() {
        if (!enabled || metricsCache.isEmpty()) {
            return;
        }

        int totalFlushed = 0;

        for (Map.Entry<String, Queue<PersistedMetricData>> entry : metricsCache.entrySet()) {
            String circuitBreakerName = entry.getKey();
            Queue<PersistedMetricData> metrics = entry.getValue();

            if (metrics.isEmpty()) {
                continue;
            }

            List<PersistedMetricData> metricsList = new ArrayList<>();
            PersistedMetricData metric;
            while ((metric = metrics.poll()) != null) {
                metricsList.add(metric);
                totalFlushed++;
            }

            if (!metricsList.isEmpty()) {
                saveMetricsToFile(circuitBreakerName, metricsList);
            }
        }

        if (totalFlushed > 0) {
            log.info("Flushed {} metrics to files", totalFlushed);
        }
        
        // Periodically clean up old files
        if (Math.random() < 0.1) { // 10% chance to run cleanup on each flush
            cleanupOldMetricsFiles();
        }
    }

    @Override
    public void shutdown() {
        if (!enabled) {
            return;
        }

        log.info("Flushing metrics before shutdown");
        flushMetricsToFiles();
    }

    private void saveMetricsToFile(String circuitBreakerName, List<PersistedMetricData> metrics) {
        if (metrics.isEmpty()) {
            return;
        }

        try {
            // Create directory for circuit breaker if it doesn't exist
            String dirName = sanitizeFileName(circuitBreakerName);
            Path circuitBreakerDir = Paths.get(basePath, dirName);
            if (!Files.exists(circuitBreakerDir)) {
                Files.createDirectories(circuitBreakerDir);
            }

            // Generate file name with timestamp
            String fileName = String.format("%s_%d.json", 
                    dirName, 
                    System.currentTimeMillis());
            Path filePath = circuitBreakerDir.resolve(fileName);

            // Write metrics to file
            objectMapper.writeValue(filePath.toFile(), metrics);
            log.debug("Wrote {} metrics to file {}", metrics.size(), filePath);
        } catch (IOException e) {
            log.error("Error writing metrics to file for {}: {}", circuitBreakerName, e.getMessage(), e);
        }
    }

    private void cleanupOldMetricsFiles() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
            long cutoffMillis = cutoffTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            
            Path baseDir = Paths.get(basePath);
            if (!Files.exists(baseDir)) {
                return;
            }
            
            // Process each circuit breaker directory
            Files.list(baseDir)
                 .filter(Files::isDirectory)
                 .forEach(cbDir -> {
                     try {
                         // Delete old files in this directory
                         Files.list(cbDir)
                              .filter(Files::isRegularFile)
                              .filter(path -> {
                                  File file = path.toFile();
                                  return file.lastModified() < cutoffMillis;
                              })
                              .forEach(path -> {
                                  try {
                                      Files.delete(path);
                                      log.debug("Deleted old metrics file: {}", path);
                                  } catch (IOException e) {
                                      log.warn("Failed to delete old metrics file {}: {}", path, e.getMessage());
                                  }
                              });
                     } catch (IOException e) {
                         log.error("Error cleaning up directory {}: {}", cbDir, e.getMessage());
                     }
                 });
        } catch (IOException e) {
            log.error("Error during metrics cleanup: {}", e.getMessage());
        }
    }

    private MetricSnapshot convertToMetricSnapshot(PersistedMetricData data) {
        // Create a service metric from the persisted data
        ServiceMetric metric = new ServiceMetric(
                data.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                (long) data.getLatency(),
                data.getErrorRate() < 0.5, // True if error rate is less than 50%
                1, // Default concurrency
                data.getSystemLoad()
        );
        
        // Return a MetricSnapshot with this metric
        return new MetricSnapshot(Collections.singletonList(metric));
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    /**
     * Simple data class for persisting metrics to JSON files
     */
    public static class PersistedMetricData {
        private LocalDateTime timestamp;
        private String circuitBreakerName;
        private double errorRate;
        private double latency;
        private double systemLoad;
        private int callVolume;

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        public String getCircuitBreakerName() {
            return circuitBreakerName;
        }

        public void setCircuitBreakerName(String circuitBreakerName) {
            this.circuitBreakerName = circuitBreakerName;
        }

        public double getErrorRate() {
            return errorRate;
        }

        public void setErrorRate(double errorRate) {
            this.errorRate = errorRate;
        }

        public double getLatency() {
            return latency;
        }

        public void setLatency(double latency) {
            this.latency = latency;
        }

        public double getSystemLoad() {
            return systemLoad;
        }

        public void setSystemLoad(double systemLoad) {
            this.systemLoad = systemLoad;
        }

        public int getCallVolume() {
            return callVolume;
        }

        public void setCallVolume(int callVolume) {
            this.callVolume = callVolume;
        }
    }
}
