package org.projectk.circuitbreaker.ml.persistence;

import lombok.extern.slf4j.Slf4j;
import org.projectk.circuitbreaker.ml.MetricSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A composite metrics persistence provider that delegates to multiple underlying providers.
 * This allows using multiple storage backends simultaneously, with data being written to all
 * and read preferentially from the most appropriate source.
 * 
 * The default implementation always uses FileBasedMetricsPersistence as the primary source,
 * with optional Redis or other implementations as secondary sources when available.
 */
@Slf4j
@Primary
@Component
public class CompositeMetricsPersistence implements MetricsPersistenceProvider {

    @Autowired
    private FileBasedMetricsPersistence filePersistence;
    
    @Autowired(required = false)
    private List<MetricsPersistenceProvider> providers = new ArrayList<>();
    
    @PostConstruct
    public void init() {
        // Filter out this component itself to avoid infinite recursion
        providers = providers.stream()
                .filter(p -> !(p instanceof CompositeMetricsPersistence))
                .collect(Collectors.toList());
        
        // Log which providers are active
        if (providers.isEmpty()) {
            log.info("Using only file-based metrics persistence");
        } else {
            log.info("Using multiple metrics persistence providers: {}", 
                    providers.stream()
                            .map(p -> p.getClass().getSimpleName())
                            .collect(Collectors.joining(", ")));
        }
    }
    
    @Override
    public void storeMetrics(MetricSnapshot snapshot, String circuitBreakerName) {
        // Always write to file-based persistence
        filePersistence.storeMetrics(snapshot, circuitBreakerName);
        
        // Write to each additional provider
        for (MetricsPersistenceProvider provider : providers) {
            try {
                provider.storeMetrics(snapshot, circuitBreakerName);
            } catch (Exception e) {
                log.warn("Failed to store metrics in {}: {}", 
                        provider.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public List<MetricSnapshot> loadHistoricalMetrics(String circuitBreakerName, Duration lookbackPeriod) {
        // Always start with file-based metrics
        List<MetricSnapshot> metrics = new ArrayList<>(filePersistence.loadHistoricalMetrics(circuitBreakerName, lookbackPeriod));
        
        // Try to load from other providers and merge results
        for (MetricsPersistenceProvider provider : providers) {
            try {
                List<MetricSnapshot> providerMetrics = provider.loadHistoricalMetrics(circuitBreakerName, lookbackPeriod);
                if (!providerMetrics.isEmpty()) {
                    log.debug("Loaded {} metrics from {}", providerMetrics.size(), 
                            provider.getClass().getSimpleName());
                    metrics.addAll(providerMetrics);
                }
            } catch (Exception e) {
                log.warn("Failed to load metrics from {}: {}", 
                        provider.getClass().getSimpleName(), e.getMessage());
            }
        }
        
        // Sort metrics by timestamp if we have metrics from multiple sources
        if (!providers.isEmpty() && !metrics.isEmpty()) {
            // Sort by the timestamp of the first metric in each snapshot
            metrics.sort(Comparator.comparing(snapshot -> {
                if (snapshot.getMetrics() != null && !snapshot.getMetrics().isEmpty()) {
                    return snapshot.getMetrics().get(0).getTimestamp();
                }
                return 0L;
            }));
        }
        
        return metrics;
    }

    @Override
    public void shutdown() {
        // Shutdown file-based persistence
        filePersistence.shutdown();
        
        // Shutdown all other providers
        for (MetricsPersistenceProvider provider : providers) {
            try {
                provider.shutdown();
            } catch (Exception e) {
                log.warn("Error shutting down {}: {}", 
                        provider.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
