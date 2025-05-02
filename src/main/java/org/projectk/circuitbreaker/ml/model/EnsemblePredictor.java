package org.projectk.circuitbreaker.ml.model;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Combines multiple neural network models to create a more robust ensemble predictor.
 * The ensemble approach helps reduce variance and overfitting compared to a single model.
 */
@Slf4j
public class EnsemblePredictor {
    private final List<SimpleNeuralNetwork> models;
    private final double[] modelWeights;
    private final int inputSize;
    private final double baselearningRate;
    
    /**
     * Creates an ensemble of neural network models
     * 
     * @param numModels Number of models in ensemble
     * @param inputSize Number of input features
     * @param learningRate Base learning rate for training
     */
    public EnsemblePredictor(int numModels, int inputSize, double learningRate) {
        this.models = new ArrayList<>(numModels);
        this.modelWeights = new double[numModels];
        this.inputSize = inputSize;
        this.baselearningRate = learningRate;
        
        // Create diverse models 
        for (int i = 0; i < numModels; i++) {
            // Vary hidden layer size for diversity
            int hiddenSize = 4 + (i * 2);
            // Configure with different momentum and regularization values for diversity
            double momentum = 0.9 - (i * 0.1); // 0.9, 0.8, 0.7, ...
            double l2Lambda = 0.001 * (i + 1); // 0.001, 0.002, 0.003, ...
            models.add(new SimpleNeuralNetwork(inputSize, hiddenSize, momentum, l2Lambda));
            modelWeights[i] = 1.0 / numModels; // Equal weights initially
        }
        
        log.info("Created ensemble predictor with {} models, inputSize={}", 
                numModels, inputSize);
    }
    
    /**
     * Generates a prediction using the weighted average of all models in the ensemble
     * 
     * @param features Input feature vector
     * @return Weighted prediction value between 0 and 1
     */
    public double predict(double[] features) {
        if (features.length != inputSize) {
            log.warn("Feature vector length {} doesn't match expected input size {}", 
                    features.length, inputSize);
            return 0.5; // Default value if input is invalid
        }
        
        double prediction = 0.0;
        for (int i = 0; i < models.size(); i++) {
            prediction += models.get(i).predict(features) * modelWeights[i];
        }
        return prediction;
    }
    
    /**
     * Trains all models in the ensemble and updates their weights
     * 
     * @param features Input feature vector
     * @param target Target/actual value to learn
     */
    public void learn(double[] features, double target) {
        if (features.length != inputSize) {
            return;
        }
        
        // Train each model with slightly different learning rates for diversity
        for (int i = 0; i < models.size(); i++) {
            // Vary learning rate slightly for each model
            double modelLearningRate = baselearningRate * (0.8 + (0.4 * i / models.size()));
            models.get(i).learn(features, target, modelLearningRate);
        }
        
        // Update model weights based on accuracy
        updateModelWeights(features, target);
    }
    
    /**
     * Updates the weights of each model based on their accuracy
     * More accurate models get higher weights in the ensemble
     */
    private void updateModelWeights(double[] features, double target) {
        double totalError = 0.0;
        double[] errors = new double[models.size()];
        
        // Calculate error for each model
        for (int i = 0; i < models.size(); i++) {
            double pred = models.get(i).predict(features);
            errors[i] = Math.abs(target - pred);
            totalError += errors[i];
        }
        
        // If all models are perfect or have same error, keep equal weights
        if (totalError <= 0.0001) {
            for (int i = 0; i < models.size(); i++) {
                modelWeights[i] = 1.0 / models.size();
            }
            return;
        }
        
        // Inverse error weighting - more accurate models get higher weights
        double weightSum = 0.0;
        for (int i = 0; i < models.size(); i++) {
            // Models with lower error get higher weights
            modelWeights[i] = (totalError - errors[i]) / ((models.size() - 1) * totalError);
            weightSum += modelWeights[i];
        }
        
        // Normalize weights to sum to 1.0
        for (int i = 0; i < models.size(); i++) {
            modelWeights[i] /= weightSum;
        }
    }
    
    /**
     * @return Average loss across all models in the ensemble
     */
    public double getAverageLoss() {
        double totalLoss = 0.0;
        for (SimpleNeuralNetwork model : models) {
            totalLoss += model.getLastLoss();
        }
        return totalLoss / models.size();
    }
    
    /**
     * Train all models in the ensemble with a batch of examples
     * 
     * @param featuresBatch Array of feature vectors
     * @param targetsBatch Array of target values
     */
    public void learnBatch(double[][] featuresBatch, double[] targetsBatch) {
        if (featuresBatch.length != targetsBatch.length || featuresBatch.length == 0) {
            log.warn("Batch sizes don't match or empty batch provided");
            return;
        }
        
        if (featuresBatch[0].length != inputSize) {
            log.warn("Feature vector length {} doesn't match expected input size {}", 
                    featuresBatch[0].length, inputSize);
            return;
        }
        
        // Train each model with the batch
        for (int i = 0; i < models.size(); i++) {
            // Vary learning rate slightly for each model
            double modelLearningRate = baselearningRate * (0.8 + (0.4 * i / models.size()));
            models.get(i).learnBatch(featuresBatch, targetsBatch, modelLearningRate);
        }
        
        // Update model weights based on the last example in the batch
        // This is a simplification - ideally we'd evaluate on validation data
        updateModelWeights(featuresBatch[featuresBatch.length-1], targetsBatch[targetsBatch.length-1]);
    }
    
    /**
     * Reset all models in the ensemble
     */
    public void reset() {
        for (SimpleNeuralNetwork model : models) {
            model.reset();
        }
        
        // Reset to equal weights
        for (int i = 0; i < models.size(); i++) {
            modelWeights[i] = 1.0 / models.size();
        }
        
        log.info("Reset all {} models in ensemble", models.size());
    }
}
