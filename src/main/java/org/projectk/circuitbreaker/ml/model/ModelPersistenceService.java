package org.projectk.circuitbreaker.ml.model;

import lombok.extern.slf4j.Slf4j;
import org.projectk.circuitbreaker.ml.persistence.MetricsPersistenceProvider;
import org.projectk.circuitbreaker.ml.persistence.TimeSeriesDataPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Service responsible for saving and loading neural network models.
 * Provides model persistence and automatic retraining of models using historical metrics.
 * <p>
 * Current implementation uses file-based storage for models, with a future upgrade path
 * to Redis once the Redis configuration is fully set up.
 */
@Slf4j
@Service
public class ModelPersistenceService {
    private static final Duration DEFAULT_LOOKBACK_PERIOD = Duration.ofDays(7);
    
    private final MetricsPersistenceProvider metricsPersistenceProvider;

    @Value("${circuit-breaker.ml.model.local-fallback-path:./models}")
    private String localFallbackPath;

    @Value("${circuit-breaker.ml.model.retraining.enabled:true}")
    private boolean retrainingEnabled;

    @Value("${circuit-breaker.ml.model.retraining.min-data-points:100}")
    private int minDataPointsForTraining;

    @Autowired
    public ModelPersistenceService(MetricsPersistenceProvider metricsPersistenceProvider) {
        this.metricsPersistenceProvider = metricsPersistenceProvider;
        log.info("Initializing model persistence service with file-based storage");
    }

    /**
     * Saves a neural network model to persistent storage
     *
     * @param model The neural network model to save
     * @param serviceName The service name to associate with this model
     * @return True if the model was saved successfully, false otherwise
     */
    public boolean saveModel(NeuralNetworkModel model, String serviceName) {
        if (model == null || serviceName == null || serviceName.isEmpty()) {
            log.warn("Cannot save null model or empty service name");
            return false;
        }
        
        return saveModelToFile(model, serviceName);
    }

    /**
     * Loads a neural network model from persistent storage
     *
     * @param serviceName The service name to load the model for
     * @return The loaded neural network model or null if not found
     */
    public NeuralNetworkModel loadModel(String serviceName) {
        if (serviceName == null || serviceName.isEmpty()) {
            log.warn("Cannot load model for null or empty service name");
            return null;
        }
        
        return loadModelFromFile(serviceName);
    }

    /**
     * Retrains a neural network model using historical metrics when the system restarts
     *
     * @param serviceName The service name associated with the model
     * @param existingModel The existing model to retrain, or null to create a new one
     * @return The trained model, or a new untrained model if training failed
     */
    public NeuralNetworkModel retrainModel(String serviceName, NeuralNetworkModel existingModel) {
        if (!retrainingEnabled) {
            log.info("Model retraining is disabled");
            return existingModel != null ? existingModel : createNewModel();
        }

        try {
            // Load historical metrics for training
            List<TimeSeriesDataPoint> historicalData = metricsPersistenceProvider.getTimeSeriesData(
                    serviceName, 
                    Instant.now().minus(DEFAULT_LOOKBACK_PERIOD), 
                    Instant.now());

            if (historicalData.isEmpty() || historicalData.size() < minDataPointsForTraining) {
                log.info("Insufficient historical data for training - using existing model");
                return existingModel != null ? existingModel : createNewModel();
            }

            log.info("Retraining model for service {} with {} data points", serviceName, historicalData.size());

            // Create a new model or use existing one
            NeuralNetworkModel retrainedModel = existingModel != null ? existingModel : createNewModel();

            // Prepare training data from historical metrics
            double[][] inputs = new double[historicalData.size()][4];
            double[][] expectedOutputs = new double[historicalData.size()][1];

            for (int i = 0; i < historicalData.size(); i++) {
                TimeSeriesDataPoint dataPoint = historicalData.get(i);

                // Feature extraction
                inputs[i][0] = dataPoint.getErrorRate();
                inputs[i][1] = normalizeLatency(dataPoint.getLatency());
                inputs[i][2] = normalizeSystemLoad(dataPoint.getSystemLoad());
                inputs[i][3] = normalizeVolume(dataPoint.getCallVolume());

                // Target: Error rate above threshold indicates service issue
                expectedOutputs[i][0] = dataPoint.getErrorRate() > 0.5 ? 1.0 : 0.0;
            }

            // Train the model (more iterations during startup for better accuracy)
            retrainedModel.train(inputs, expectedOutputs, 1000, 0.01);

            log.info("Successfully retrained model for service {}", serviceName);
            return retrainedModel;

        } catch (Exception e) {
            log.error("Failed to retrain model: {}", e.getMessage(), e);
            return existingModel != null ? existingModel : createNewModel();
        }
    }

    /**
     * Saves a model to a local file as fallback persistence
     *
     * @param model The model to save
     * @param serviceName The service name associated with the model
     * @return True if successfully saved, false otherwise
     */
    private boolean saveModelToFile(NeuralNetworkModel model, String serviceName) {
        try {
            // Create models directory if it doesn't exist
            Path dirPath = Paths.get(localFallbackPath);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // Define file path for the model
            String fileName = serviceName.replaceAll("[^a-zA-Z0-9.-]", "_") + ".model";
            String filePath = Paths.get(localFallbackPath, fileName).toString();

            // Serialize the model to file
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
                oos.writeObject(model);
            }

            log.info("Saved model for service {} to local file: {}", serviceName, filePath);
            return true;

        } catch (IOException e) {
            log.error("Failed to save model to file: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Loads a model from a local file as fallback persistence
     *
     * @param serviceName The service name associated with the model
     * @return The loaded model or null if not found
     */
    private NeuralNetworkModel loadModelFromFile(String serviceName) {
        try {
            // Define file path for the model
            String fileName = serviceName.replaceAll("[^a-zA-Z0-9.-]", "_") + ".model";
            String filePath = Paths.get(localFallbackPath, fileName).toString();

            // Deserialize the model from file
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
                SimpleNeuralNetwork model = (SimpleNeuralNetwork) ois.readObject();
                log.info("Loaded model for service {} from local file: {}", serviceName, filePath);
                return model;
            }

        } catch (IOException | ClassNotFoundException e) {
            log.error("Failed to load model from file: {}", e.getMessage(), e);
            return null;
        }
    }

    @Autowired
    private NeuralNetworkFactory neuralNetworkFactory;
    
    /**
     * Helper method to create a new model with default parameters
     */
    private NeuralNetworkModel createNewModel() {
        log.info("Creating new neural network model with default parameters");
        return neuralNetworkFactory.createNeuralNetwork(4, 8, 1);
    }

    /**
     * Helper methods for feature normalization
     */
    private double normalizeLatency(double latencyMs) {
        // Normalize using exponential function: 1 - exp(-latency/threshold)
        // This gives a value between 0 and 1, with higher latencies closer to 1
        return 1.0 - Math.exp(-latencyMs / 500.0); // 500ms as reference point
    }

    private double normalizeVolume(long callVolume) {
        // Normalize volume using log scale
        return Math.min(1.0, Math.log10(callVolume + 1) / 5.0); // log10(100000) ≈ 5
    }

    private double normalizeSystemLoad(double systemLoad) {
        // Simple normalization assuming system load is between 0-1
        // If system load can be > 1, use: min(systemLoad / maxExpectedLoad, 1.0)
        return Math.min(systemLoad, 1.0);
    }

}
