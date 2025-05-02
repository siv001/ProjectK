package org.projectk.circuitbreaker.ml;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.projectk.circuitbreaker.ml.persistence.MetricsPersistenceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.projectk.circuitbreaker.ml.model.NeuralNetworkModel;
import org.projectk.circuitbreaker.ml.model.NeuralNetworkFactory;

// Note: This is using the simple AnomalyDetector from the ml package, not the enhanced one in model package

/**
 * Machine Learning enhanced Circuit Breaker implementation that adaptively 
 * adjusts circuit breaker parameters based on observed metrics
 */
@Slf4j
@Component("mlCircuitBreakerImpl")
public class MLCircuitBreaker {
    @Value("${circuit.breaker.name:defaultBreaker}")
    private String name;
    
    @Autowired(required = false)
    private MetricsPersistenceProvider metricsPersistence;
    
    private MetricsCollector metricsCollector;
    private ThresholdPredictor predictor;
    private MLPerformanceMonitor performanceMonitor;
    private AnomalyDetector anomalyDetector;
    private AdaptiveConfigManager configManager;
    
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    private volatile CircuitBreaker circuitBreaker;
    
    // Added to prevent configuration thrashing
    private final AtomicLong lastConfigUpdateTime = new AtomicLong(0);
    private Duration currentWaitDuration; // Track current wait duration for comparison
    private static final long MIN_CONFIG_UPDATE_INTERVAL_MS = 60000; // 1 minute
    
    // Feature toggle for A/B testing
    private boolean useMLConfig = true;
    
    // Operation count and reporting frequency
    private int operationCount = 0;
    private static final int METRICS_REPORT_INTERVAL = 1000; // Report metrics every 1000 operations
    
    // Track models and circuit breakers by service name
    private final ConcurrentHashMap<String, NeuralNetworkModel> serviceModels = new ConcurrentHashMap<>();
    
    @Autowired
    private NeuralNetworkFactory neuralNetworkFactory;

    /**
     * Constructs dependencies and initializes the ML circuit breaker after Spring injection
     * with error handling for ML component failures
     */
    @PostConstruct
    public void init() {
        try {
            // Initialize core component - metrics collector
            this.metricsCollector = new MetricsCollector();
            
            // Initialize ML components with error handling
            initializeMlComponents();
            
            // Create initial circuit breaker (uses predictor values)
            this.circuitBreaker = createCircuitBreaker();
            
            log.info("MLCircuitBreaker initialized with initial parameters: window={}, threshold={}, waitDuration={}s",
                    predictor.getOptimalWindowSize(), predictor.getCurrentThreshold(), 
                    currentWaitDuration.getSeconds());
        } catch (Exception e) {
            // Log the error but continue with safe defaults
            log.error("Error during MLCircuitBreaker initialization, using safe defaults", e);
            
            // Ensure we have a working circuit breaker with conservative defaults
            initializeWithSafeDefaults();
        }
    }
    
    /**
     * Initializes ML components with error handling for each component
     * Protected visibility to allow overriding in tests
     */
    protected void initializeMlComponents() {
        // Initialize predictor
        try {
            this.predictor = new ThresholdPredictor();
        } catch (Exception e) {
            log.error("Failed to initialize ThresholdPredictor, using fallback", e);
            this.predictor = createFallbackPredictor();
        }
        
        // Initialize performance monitor
        try {
            this.performanceMonitor = new MLPerformanceMonitor(meterRegistry);
        } catch (Exception e) {
            log.error("Failed to initialize MLPerformanceMonitor, using no-op implementation", e);
            this.performanceMonitor = createNoOpPerformanceMonitor();
        }
        
        // Initialize anomaly detector
        try {
            this.anomalyDetector = new AnomalyDetector();
        } catch (Exception e) {
            log.error("Failed to initialize AnomalyDetector, using conservative detector", e);
            this.anomalyDetector = createConservativeAnomalyDetector();
        }
        
        // Initialize config manager
        try {
            this.configManager = new AdaptiveConfigManager(circuitBreakerRegistry, predictor);
            // Initial wait duration from predictor
            this.currentWaitDuration = predictor.getOptimalWaitDuration();
        } catch (Exception e) {
            log.error("Failed to initialize AdaptiveConfigManager, using static configuration", e);
            this.configManager = null; // Will use static config instead
            this.currentWaitDuration = Duration.ofSeconds(30); // Conservative default
        }
    }
    
    /**
     * Creates a fallback predictor with conservative static values
     */
    private ThresholdPredictor createFallbackPredictor() {
        // Create a minimal implementation that returns conservative defaults
        return new ThresholdPredictor() {
            @Override
            public double getCurrentThreshold() { return 50.0; } // 50% failure threshold
            
            @Override
            public int getOptimalWindowSize() { return 100; } // Window size of 100
            
            @Override
            public Duration getOptimalWaitDuration() { return Duration.ofSeconds(30); } // 30 second wait
        };
    }
    
    /**
     * Creates a no-op performance monitor that doesn't affect application behavior
     */
    private MLPerformanceMonitor createNoOpPerformanceMonitor() {
        // Return a minimal implementation that does nothing
        return new MLPerformanceMonitor(meterRegistry) {
            @Override
            public void recordPredictionAccuracy(boolean actual, double predicted) { /* No-op */ }
            
            @Override
            public void recordModelMetrics(MetricSnapshot metrics) { /* No-op */ }
            
            @Override
            public void recordParameterChange(int windowSize, float threshold, Duration waitDuration, double currentErrorRate) { /* No-op */ }
        };
    }
    
    /**
     * Creates a conservative anomaly detector that rarely reports anomalies
     */
    private AnomalyDetector createConservativeAnomalyDetector() {
        // Return a minimal implementation that rarely reports anomalies
        return new AnomalyDetector() {
            @Override
            public boolean isAnomaly(MetricSnapshot metrics) { return false; } // Never report anomalies
        };
    }
    
    /**
     * Initializes circuit breaker with safe default values when ML initialization fails
     */
    private void initializeWithSafeDefaults() {
        // Ensure we have basic components
        if (this.metricsCollector == null) {
            this.metricsCollector = new MetricsCollector();
        }
        
        if (this.predictor == null) {
            this.predictor = createFallbackPredictor();
        }
        
        // Create a circuit breaker with conservative defaults
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(100)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
                
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(name, defaultConfig);
        this.currentWaitDuration = Duration.ofSeconds(30);
        
        // Disable ML-based configuration
        this.useMLConfig = false;
        
        log.warn("MLCircuitBreaker initialized with safe defaults. ML-based configuration is DISABLED.");
    }

    /**
     * Clean up resources when the application is shutting down
     */
    @PreDestroy
    public void shutdown() {
        if (metricsPersistence != null) {
            metricsPersistence.shutdown();
            log.info("Metrics persistence shutdown complete");
        }
        
        // Save all models on shutdown
        saveAllModels();
    }
    
    /**
     * Updates the neural network model for a specific service
     * 
     * @param serviceName The name of the service to update model for
     * @param model The new neural network model
     */
    public void updateModel(String serviceName, NeuralNetworkModel model) {
        if (serviceName == null || model == null) {
            log.warn("Cannot update model with null service name or model");
            return;
        }
        
        // Store the model in memory
        serviceModels.put(serviceName, model);
        log.info("Updated model for service: {}", serviceName);
    }
    
    /**
     * Retrieves the set of service names that have been registered with this circuit breaker
     * 
     * @return Set of service names
     */
    public Set<String> getServiceNames() {
        // Return names from both serviceModels and any other service tracking mechanism
        return serviceModels.keySet();
    }

    /**
     * Gets the neural network model for a specific service
     * 
     * @param serviceName The name of the service
     * @return The neural network model or null if not found
     */
    public NeuralNetworkModel getModel(String serviceName) {
        // If service doesn't have a model yet, create one using the factory
        if (!serviceModels.containsKey(serviceName)) {
            createModelForService(serviceName);
        }
        return serviceModels.get(serviceName);
    }
    
    /**
     * Creates a new model for a service using the neural network factory
     * 
     * @param serviceName The name of the service to create a model for
     * @return The newly created neural network model
     */
    private NeuralNetworkModel createModelForService(String serviceName) {
        log.info("Creating new model for service: {}", serviceName);
        // Standard model config: 4 inputs, 8 hidden neurons, 1 output
        NeuralNetworkModel model = neuralNetworkFactory.createNeuralNetwork(4, 8, 1);
        serviceModels.put(serviceName, model);
        return model;
    }
    
    /**
     * Saves all neural network models to persistence storage
     */
    private void saveAllModels() {
        // This method requires ModelPersistenceService, but since it's called
        // from shutdown, we'll just log if not available rather than inject it
        try {
            Object modelPersistenceService = applicationContext.getBean("modelPersistenceService");
            if (modelPersistenceService != null && serviceModels.size() > 0) {
                log.info("Saving {} models to persistence storage", serviceModels.size());
                
                // Call the saveModel method via reflection to avoid direct dependency
                for (String serviceName : serviceModels.keySet()) {
                    try {
                        NeuralNetworkModel model = serviceModels.get(serviceName);
                        java.lang.reflect.Method saveMethod = modelPersistenceService.getClass()
                            .getMethod("saveModel", NeuralNetworkModel.class, String.class);
                        saveMethod.invoke(modelPersistenceService, model, serviceName);
                        log.debug("Saved model for service: {}", serviceName);
                    } catch (Exception e) {
                        log.warn("Failed to save model for service {}: {}", serviceName, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.info("Model persistence service not available for saving models");
        }
    }
    
    /**
     * Creates the initial circuit breaker with default settings
     */
    private CircuitBreaker createCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(predictor.getOptimalWindowSize())
                .failureRateThreshold((float) predictor.getCurrentThreshold())
                .waitDurationInOpenState(predictor.getOptimalWaitDuration())
                .build();

        return circuitBreakerRegistry.circuitBreaker(name, config);
    }

    /**
     * Updates the circuit breaker configuration while preserving state
     * with rate limiting to prevent excessive config changes
     *
     * @param metrics Current metrics snapshot used to determine new config
     */
    private void updateCircuitBreakerConfigIfNeeded(MetricSnapshot metrics) {
        long now = System.currentTimeMillis();
        long lastUpdate = lastConfigUpdateTime.get();
        
        // Don't update too frequently
        if (now - lastUpdate < MIN_CONFIG_UPDATE_INTERVAL_MS) {
            log.debug("Skipping config update, too soon since last update ({} ms ago)", now - lastUpdate);
            return;
        }
        
        // Get updated configuration from ML
        CircuitBreakerConfig newConfig = configManager.getUpdatedConfig(metrics);
        Duration newWaitDuration = predictor.getOptimalWaitDuration();
        
        // Check if the change is significant enough to warrant an update
        if (configManager.isSignificantChange(newConfig, newWaitDuration, currentWaitDuration)) {
            try {
                // Store the current state
                CircuitBreaker.State currentState = circuitBreaker.getState();
    
                // Remove existing circuit breaker
                circuitBreakerRegistry.remove(name);
    
                // Create new circuit breaker with updated config
                circuitBreaker = circuitBreakerRegistry.circuitBreaker(name, newConfig);
                
                // Update our tracked wait duration
                this.currentWaitDuration = newWaitDuration;
    
                // Restore previous state if needed
                if (currentState == CircuitBreaker.State.OPEN) {
                    circuitBreaker.transitionToOpenState();
                } else if (currentState == CircuitBreaker.State.HALF_OPEN) {
                    circuitBreaker.transitionToHalfOpenState();
                }
    
                // Update timestamp of last successful update
                lastConfigUpdateTime.set(now);
                
                // Record parameter change for performance monitoring
                performanceMonitor.recordParameterChange(
                    newConfig.getSlidingWindowSize(),
                    newConfig.getFailureRateThreshold(),
                    newWaitDuration,
                    metrics.getErrorRate()
                );
                
                log.info("Circuit breaker configuration updated for {}", name);
            } catch (Exception e) {
                log.error("Failed to update circuit breaker configuration", e);
            }
        } else {
            log.debug("No significant change in circuit breaker configuration, skipping update");
        }
    }
    
    /**
     * Toggles the use of ML-based configuration
     * Useful for A/B testing and validating ML effectiveness
     * 
     * @param enabled Whether to use ML-based configuration
     */
    public void toggleMLConfig(boolean enabled) {
        this.useMLConfig = enabled;
        log.info("ML-based configuration for circuit breaker {} is now {}", 
                name, enabled ? "enabled" : "disabled");
    }

    /**
     * Executes an operation with ML-enhanced circuit breaking
     * with robust error handling for ML component failures
     *
     * @param operation The operation to execute
     * @return Mono with the operation result
     */
    public <T> Mono<T> executeWithML(Supplier<Mono<T>> operation) {
        return Mono.fromSupplier(() -> {
            // Capture operation start time
            long startTime = System.nanoTime();
            
            // Get current metrics with error handling
            MetricSnapshot snapshot = safeGetCurrentMetrics();
            
            // Track operation count
            operationCount++;
            if (operationCount % METRICS_REPORT_INTERVAL == 0) {
                log.info("Circuit breaker {} has processed {} operations", 
                        name, operationCount);
            }

            // Update ML model and possibly circuit breaker config with error handling
            if (useMLConfig) {
                try {
                    // Check for anomalies
                    boolean isAnomaly = false;
                    try {
                        isAnomaly = anomalyDetector.isAnomaly(snapshot);
                    } catch (Exception e) {
                        log.warn("Error detecting anomalies, proceeding with ML updates", e);
                    }
                    
                    if (!isAnomaly) {
                        // Update the ML model based on current metrics
                        try {
                            predictor.updateThresholds(snapshot);
                        } catch (Exception e) {
                            log.error("Error updating ML thresholds, skipping model update", e);
                        }
                        
                        // Try to update circuit breaker configuration (throttled internally)
                        try {
                            updateCircuitBreakerConfigIfNeeded(snapshot);
                        } catch (Exception e) {
                            log.error("Error updating circuit breaker config, keeping current settings", e);
                        }
                    }
                } catch (Exception e) {
                    log.error("Unexpected error in ML processing, continuing with existing configuration", e);
                }
            }

            // This is the core circuit breaker logic - must be robust
            try {
                return circuitBreaker.decorateSupplier(() -> {
                    try {
                        T result = operation.get().block();
                        safeRecordSuccess(startTime);
                        return result;
                    } catch (Exception e) {
                        safeRecordFailure(startTime, e);
                        throw e;
                    }
                }).get();
            } catch (Exception e) {
                log.error("Circuit breaker execution failed", e);
                throw e; // Propagate the exception after logging
            }
        });
    }
    
    /**
     * Safely gets current metrics with error handling
     */
    protected MetricSnapshot safeGetCurrentMetrics() {
        try {
            MetricSnapshot snapshot = metricsCollector.getCurrentMetrics();
            
            // Store metrics for persistence if service is available
            if (metricsPersistence != null) {
                metricsPersistence.storeMetrics(snapshot, name);
            }
            
            return snapshot;
        } catch (Exception e) {
            log.warn("Failed to collect metrics: {}", e.getMessage());
            return getFallbackMetrics(e);
        }
    }
    
    /**
     * Gets fallback metrics when the primary source fails
     * Protected for better testability
     * 
     * @param e The exception that caused the fallback
     * @return A fallback MetricSnapshot
     */
    protected MetricSnapshot getFallbackMetrics(Exception e) {
        // Creating an empty list of metrics (since MetricSnapshot requires metrics)
        return metricsCollector.createEmptySnapshot();
    }
    
    /**
     * Records a successful operation with error handling
     */
    private void safeRecordSuccess(long startTime) {
        try {
            recordSuccess(startTime);
        } catch (Exception e) {
            log.warn("Error recording successful operation", e);
        }
    }
    
    /**
     * Records a failed operation with error handling
     */
    private void safeRecordFailure(long startTime, Exception e) {
        try {
            recordFailure(startTime, e);
        } catch (Exception ex) {
            log.warn("Error recording failed operation", ex);
        }
    }

    /**
     * Records a successful operation
     *
     * @param startTime The start time of the operation
     */
    private void recordSuccess(long startTime) {
        long latency = System.nanoTime() - startTime;
        ServiceMetric metric = new ServiceMetric(
            System.currentTimeMillis(),
            latency,
            true,
            circuitBreaker.getMetrics().getNumberOfBufferedCalls(),
            getSystemLoad()
        );
        metricsCollector.recordMetric(metric);
        performanceMonitor.recordPredictionAccuracy(true, predictor.getLastPrediction());
    }

    /**
     * Records a failed operation
     *
     * @param startTime The start time of the operation
     * @param e The exception that caused the failure
     */
    private void recordFailure(long startTime, Exception e) {
        long latency = System.nanoTime() - startTime;
        ServiceMetric metric = new ServiceMetric(
            System.currentTimeMillis(),
            latency,
            false,
            circuitBreaker.getMetrics().getNumberOfBufferedCalls(),
            getSystemLoad()
        );
        metricsCollector.recordMetric(metric);
        performanceMonitor.recordPredictionAccuracy(false, predictor.getLastPrediction());
        log.error("Circuit breaker operation failed", e);
    }

    /**
     * Gets the current system load
     */
    private double getSystemLoad() {
        return ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    }
    
    /**
     * Generates and logs a performance report every hour
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void generatePerformanceReport() {
        performanceMonitor.logPerformanceReport();
    }
    
    /**
     * Gets current circuit breaker metrics for monitoring
     * 
     * @return CircuitBreaker.Metrics object 
     */
    public CircuitBreaker.Metrics getCircuitBreakerMetrics() {
        return circuitBreaker.getMetrics();
    }
    
    /**
     * Gets current circuit breaker state
     * 
     * @return Current circuit breaker state
     */
    public CircuitBreaker.State getState() {
        return circuitBreaker.getState();
    }
}
