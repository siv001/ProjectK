package org.projectk.circuitbreaker.ml.model;

import java.io.Serializable;

/**
 * Common interface for neural network implementations to allow
 * easy switching between different implementations.
 */
public interface NeuralNetworkModel extends Serializable {
    
    /**
     * Make a prediction based on input features
     * 
     * @param input Array of input features
     * @return Predicted value between 0 and 1
     */
    double predict(double[] input);
    
    /**
     * Train the model on batch data
     * 
     * @param inputs Array of input feature vectors
     * @param expectedOutputs Array of expected output values
     * @param iterations Number of training iterations
     * @param learningRate Learning rate for weight updates
     * @return Final training loss
     */
    double train(double[][] inputs, double[][] expectedOutputs, int iterations, double learningRate);
    
    /**
     * Get the last calculated loss value
     */
    double getLastLoss();
    
    /**
     * Create a deep copy of the neural network
     */
    NeuralNetworkModel copy();
}
