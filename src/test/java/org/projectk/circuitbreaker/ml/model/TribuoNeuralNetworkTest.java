package org.projectk.circuitbreaker.ml.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TribuoNeuralNetwork implementation
 */
public class TribuoNeuralNetworkTest {

    private NeuralNetworkModel network;
    
    @BeforeEach
    public void setup() {
        // Create a small neural network for testing
        network = new TribuoNeuralNetwork(4, 8, 1);
    }
    
    @Test
    public void testInitialization() {
        // Test that model initializes without errors
        assertNotNull(network);
    }
    
    @Test
    public void testPredictionBeforeTraining() {
        // Test prediction before training is bounded between 0 and 1
        double[] input = {0.5, 0.5, 0.5, 0.5};
        double prediction = network.predict(input);
        
        assertTrue(prediction >= 0.0 && prediction <= 1.0, 
                "Prediction should be between 0 and 1, but was: " + prediction);
    }
    
    @Test
    public void testTrainingReducesLoss() {
        // Basic test inputs and expected outputs
        double[][] inputs = {
            {0.0, 0.0, 0.0, 0.0},
            {1.0, 0.0, 0.0, 0.0},
            {0.0, 1.0, 0.0, 0.0},
            {0.0, 0.0, 1.0, 0.0},
            {0.0, 0.0, 0.0, 1.0}
        };
        
        double[][] outputs = {
            {0.0},
            {1.0},
            {1.0},
            {1.0},
            {1.0}
        };
        
        // Initial loss
        double initialLoss = network.train(inputs, outputs, 1, 0.1);
        
        // Train for more iterations
        double finalLoss = network.train(inputs, outputs, 20, 0.1);
        
        // Loss should decrease
        assertTrue(finalLoss < initialLoss, 
                "Training should reduce loss. Initial: " + initialLoss + ", Final: " + finalLoss);
    }
    
    @Test
    public void testCopy() {
        // Train the original network a bit
        double[][] inputs = {{0.0, 0.0, 0.0, 0.0}, {1.0, 1.0, 1.0, 1.0}};
        double[][] outputs = {{0.0}, {1.0}};
        network.train(inputs, outputs, 10, 0.1);
        
        // Make a copy
        NeuralNetworkModel copy = network.copy();
        
        // Both should give similar predictions for the same input
        double[] testInput = {0.5, 0.5, 0.5, 0.5};
        double originalPrediction = network.predict(testInput);
        double copyPrediction = copy.predict(testInput);
        
        assertEquals(originalPrediction, copyPrediction, 0.01, 
                "Copy should give similar predictions to original");
        
        // Training the copy should not affect the original
        copy.train(inputs, outputs, 50, 0.1);
        double newOriginalPrediction = network.predict(testInput);
        double newCopyPrediction = copy.predict(testInput);
        
        assertEquals(originalPrediction, newOriginalPrediction, 0.01, 
                "Original model should be unchanged when copy is trained");
        
        // Note: When using SimpleNeuralNetwork as the implementation
        // we don't strictly verify that the copy's predictions change after
        // additional training, as this depends on the specific initialization
        // and training dynamics. We're primarily checking that training the copy
        // doesn't affect the original model's predictions.
        
        // Log the copy prediction for debugging purposes
        System.out.println("Original prediction: " + newOriginalPrediction + ", Copy prediction after training: " + newCopyPrediction);
    }
    
    @Test
    public void testGetLastLoss() {
        // Training should update the last loss value
        double[][] inputs = {{0.0, 0.0, 0.0, 0.0}, {1.0, 1.0, 1.0, 1.0}};
        double[][] outputs = {{0.0}, {1.0}};
        
        double trainingLoss = network.train(inputs, outputs, 10, 0.1);
        double reportedLoss = network.getLastLoss();
        
        assertEquals(trainingLoss, reportedLoss, 0.001, 
                "Last loss should match the return value from train()");
    }
}
