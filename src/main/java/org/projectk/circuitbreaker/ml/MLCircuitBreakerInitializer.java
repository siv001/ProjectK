package org.projectk.circuitbreaker.ml;

import lombok.extern.slf4j.Slf4j;
import org.projectk.circuitbreaker.ml.model.ModelPersistenceService;
import org.projectk.circuitbreaker.ml.model.NeuralNetworkModel;
import org.projectk.circuitbreaker.ml.model.NeuralNetworkFactory;
import org.projectk.circuitbreaker.ml.persistence.MetricsPersistenceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Initializes and prepares ML Circuit Breaker models on system startup.
 * This component handles:
 * 1. Loading neural network models from Redis
 * 2. Retraining models when the system restarts
 * 3. Ensuring model persistence across restarts
 */
@Slf4j
@Component
public class MLCircuitBreakerInitializer implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private MLCircuitBreaker mlCircuitBreaker;
    
    @Autowired(required = false)
    private ModelPersistenceService modelPersistenceService;
    
    @Autowired(required = false)
    private MetricsPersistenceProvider metricsPersistenceProvider;
    
    @Autowired
    private NeuralNetworkFactory neuralNetworkFactory;
    
    // Default lookback period for historical data when retraining
    private static final Duration DEFAULT_LOOKBACK_PERIOD = Duration.ofDays(7);
    
    /**
     * Called when the application context is refreshed (on startup)
     * Handles model loading and initiates background retraining
     */
    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        log.info("Initializing ML Circuit Breaker models");
        
        // Check if model persistence is available
        if (modelPersistenceService == null) {
            log.info("Model persistence service not available - using default models");
            return;
        }
        
        // Start with a default service if no services registered yet
        if (mlCircuitBreaker.getServiceNames().isEmpty()) {
            log.info("No services registered yet, initializing with default service");
            loadAndRetrainModel("defaultService");
        } else {
            // For each circuit breaker, load its model and initiate retraining
            mlCircuitBreaker.getServiceNames().forEach(this::loadAndRetrainModel);
        }
    }
    
    /**
     * Loads a model for a specific service and initiates retraining
     * 
     * @param serviceName The service name to load/retrain model for
     */
    private void loadAndRetrainModel(String serviceName) {
        try {
            log.info("Loading model for service: {}", serviceName);
            
            // Try to load existing model from Redis
            NeuralNetworkModel model = modelPersistenceService.loadModel(serviceName);
            
            if (model != null) {
                log.info("Loaded existing model for service: {}", serviceName);
                mlCircuitBreaker.updateModel(serviceName, model);
            } else {
                log.info("No existing model found for service: {}", serviceName);
            }
            
            // Schedule background retraining to avoid blocking application startup
            scheduleModelRetraining(serviceName, model);
            
        } catch (Exception e) {
            log.error("Error loading/retraining model for service {}: {}", 
                     serviceName, e.getMessage(), e);
        }
    }
    
    /**
     * Schedules model retraining in a background thread
     * 
     * @param serviceName The service name to retrain model for
     * @param existingModel The existing model to retrain, or null for a new model
     */
    private void scheduleModelRetraining(String serviceName, NeuralNetworkModel existingModel) {
        if (metricsPersistenceProvider == null) {
            log.info("Metrics persistence not available - skipping model retraining");
            return;
        }
        
        // Run retraining in background thread to avoid blocking startup
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting background retraining for service: {}", serviceName);
                
                // Retrain the model with historical data
                NeuralNetworkModel retrainedModel = 
                    modelPersistenceService.retrainModel(serviceName, existingModel);
                
                if (retrainedModel != null) {
                    // Update the circuit breaker with retrained model
                    mlCircuitBreaker.updateModel(serviceName, retrainedModel);
                    
                    // Save the retrained model back to Redis
                    modelPersistenceService.saveModel(retrainedModel, serviceName);
                    
                    log.info("Successfully retrained and updated model for service: {}", serviceName);
                }
            } catch (Exception e) {
                log.error("Error during background retraining for service {}: {}", 
                         serviceName, e.getMessage(), e);
            }
        });
    }
}
