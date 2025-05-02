package org.projectk.circuitbreaker.ml.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Factory class to create appropriate neural network implementations
 * based on configuration.
 */
@Slf4j
@Component
public class NeuralNetworkFactory {

    @Value("${circuit-breaker.ml.implementation:simple}")
    private String mlImplementation;
    
    /**
     * Creates a neural network with the configured implementation
     * 
     * @param inputSize Number of input features
     * @param hiddenSize Number of neurons in hidden layer
     * @param outputSize Number of output neurons (usually 1)
     * @return A neural network implementation that implements NeuralNetworkModel
     */
    public NeuralNetworkModel createNeuralNetwork(int inputSize, int hiddenSize, int outputSize) {
        if ("tribuo".equals(mlImplementation)) {
            log.info("Creating Tribuo neural network implementation");
            return new TribuoNeuralNetwork(inputSize, hiddenSize, outputSize);
        } else {
            log.info("Creating simple neural network implementation");
            return new SimpleNeuralNetwork(inputSize, hiddenSize, outputSize);
        }
    }
    
    /**
     * Get the current ML implementation type name
     * 
     * @return The name of the configured ML implementation ("simple" or "tribuo")
     */
    public String getImplementationType() {
        return mlImplementation;
    }
}
