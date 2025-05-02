package org.projectk.circuitbreaker.ml;

/**
 * Simple implementation of online gradient descent for learning circuit breaker parameters
 */
public class OnlineGradientDescent {
    private double[] weights;
    private final double learningRate;

    /**
     * Creates a new gradient descent model
     * 
     * @param learningRate Learning rate for the model
     */
    public OnlineGradientDescent(double learningRate) {
        this.learningRate = learningRate;
        this.weights = new double[5]; // Number of features
    }

    /**
     * Predicts outcome based on input features
     * 
     * @param features Array of input features
     * @return Predicted value between 0 and 1
     */
    public double predict(double[] features) {
        double prediction = 0.0;
        for (int i = 0; i < features.length; i++) {
            prediction += weights[i] * features[i];
        }
        return sigmoid(prediction);
    }

    /**
     * Updates the model based on features and actual outcomes
     * 
     * @param features Feature array
     * @param actual Actual outcome array
     */
    public void learn(double[] features, double[] actual) {
        double predicted = predict(features);
        double error = actual[0] - predicted;

        for (int i = 0; i < weights.length; i++) {
            weights[i] += learningRate * error * features[i];
        }
    }

    /**
     * Sigmoid activation function
     */
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
