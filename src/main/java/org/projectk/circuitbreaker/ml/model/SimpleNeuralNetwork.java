package org.projectk.circuitbreaker.ml.model;

import java.io.*;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;

/**
 * Simple feed-forward neural network implementation with one hidden layer
 * for more powerful non-linear modeling compared to linear models.
 * Includes batch learning, momentum, and regularization.
 */
@Slf4j
public class SimpleNeuralNetwork implements NeuralNetworkModel {
    private static final long serialVersionUID = 1L;
    private double[][] weightsLayer1; // input -> hidden
    private double[] biasesLayer1;
    private double[] weightsLayer2;  // hidden -> output
    private double biasLayer2;
    private final int inputSize;
    private final int hiddenSize;
    private static final Random random = new Random();
    
    // Momentum parameters
    private double[][] weightsLayer1Velocity;
    private double[] biasesLayer1Velocity;
    private double[] weightsLayer2Velocity;
    private double biasLayer2Velocity;
    private double momentum = 0.9; // Default momentum value
    
    // Regularization parameter
    private double l2Lambda = 0.001; // Default L2 regularization strength
    
    // For tracking training metrics
    private double lastLoss = Double.MAX_VALUE;
    private long trainingSteps = 0;
    
    /**
     * Creates a neural network with specified input and hidden layer sizes
     * 
     * @param inputSize Number of input features
     * @param hiddenSize Number of neurons in hidden layer
     */
    /**
     * Creates a neural network with specified input and hidden layer sizes
     * 
     * @param inputSize Number of input features
     * @param hiddenSize Number of neurons in hidden layer
     */
    public SimpleNeuralNetwork(int inputSize, int hiddenSize) {
        this(inputSize, hiddenSize, 1); // Default to single output
    }
    
    /**
     * Creates a neural network with specified input, hidden layer, and output sizes
     * 
     * @param inputSize Number of input features
     * @param hiddenSize Number of neurons in hidden layer
     * @param outputSize Number of output neurons (currently only supports 1)
     */
    public SimpleNeuralNetwork(int inputSize, int hiddenSize, int outputSize) {
        if (outputSize != 1) {
            log.warn("Only single output is currently supported, using outputSize=1");
        }
        
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        
        // Initialize weights with small random values
        this.weightsLayer1 = new double[inputSize][hiddenSize];
        this.biasesLayer1 = new double[hiddenSize];
        this.weightsLayer2 = new double[hiddenSize];
        this.biasLayer2 = 0.0;
        
        // Initialize momentum velocities to zero
        this.weightsLayer1Velocity = new double[inputSize][hiddenSize];
        this.biasesLayer1Velocity = new double[hiddenSize];
        this.weightsLayer2Velocity = new double[hiddenSize];
        this.biasLayer2Velocity = 0.0;
        
        // Xavier initialization for better convergence
        double scale = Math.sqrt(2.0 / (inputSize + hiddenSize));
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                weightsLayer1[i][j] = (random.nextDouble() * 2 - 1) * scale;
            }
        }
        
        for (int j = 0; j < hiddenSize; j++) {
            weightsLayer2[j] = (random.nextDouble() * 2 - 1) * scale;
        }
        
        log.debug("Initialized SimpleNeuralNetwork with inputSize={}, hiddenSize={}", 
                 inputSize, hiddenSize);
    }
    
    /**
     * Creates a neural network with specified input and hidden layer sizes,
     * plus hyperparameters for momentum and regularization
     * 
     * @param inputSize Number of input features
     * @param hiddenSize Number of neurons in hidden layer
     * @param momentum Momentum coefficient (0-1) for faster convergence
     * @param l2Lambda L2 regularization strength to prevent overfitting
     */
    public SimpleNeuralNetwork(int inputSize, int hiddenSize, double momentum, double l2Lambda) {
        this(inputSize, hiddenSize);
        this.momentum = momentum;
        this.l2Lambda = l2Lambda;
        
        log.debug("Initialized SimpleNeuralNetwork with hyperparameters: momentum={}, l2Lambda={}",
                 momentum, l2Lambda);
    }
    
    /**
     * Performs forward pass through the network to make a prediction
     * 
     * @param features Input feature vector
     * @return Prediction value between 0 and 1
     */
    public double predict(double[] features) {
        if (features.length != inputSize) {
            log.warn("Feature vector length {} doesn't match expected input size {}", 
                    features.length, inputSize);
            return 0.5; // Default value if input is invalid
        }
        
        // Forward pass
        double[] hidden = computeHiddenLayer(features);
        double output = computeOutputLayer(hidden);
        return sigmoid(output);
    }
    
    /**
     * Updates network weights based on prediction error
     * 
     * @param features Input feature vector
     * @param target Target/actual value to learn
     * @param learningRate How quickly to adjust weights
     */
    public void learn(double[] features, double target, double learningRate) {
        if (features.length != inputSize) {
            log.warn("Feature vector length {} doesn't match expected input size {}", 
                    features.length, inputSize);
            return;
        }
        
        // Forward pass
        double[] hidden = computeHiddenLayer(features);
        double output = computeOutputLayer(hidden);
        double prediction = sigmoid(output);
        
        // Calculate loss with L2 regularization
        double l2Term = 0.0;
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                l2Term += weightsLayer1[i][j] * weightsLayer1[i][j];  
            }
        }
        for (int j = 0; j < hiddenSize; j++) {
            l2Term += weightsLayer2[j] * weightsLayer2[j];
        }
        double loss = Math.pow(target - prediction, 2) + (l2Lambda * l2Term / 2.0);
        updateTrainingMetrics(loss);
        
        // Backpropagation
        // Output layer error
        double outputError = (target - prediction) * prediction * (1 - prediction);
        
        // Calculate gradients with L2 regularization
        double[] weightsLayer2Gradients = new double[hiddenSize];
        for (int j = 0; j < hiddenSize; j++) {
            weightsLayer2Gradients[j] = (outputError * hidden[j]) - (l2Lambda * weightsLayer2[j]);
            // Update with momentum
            weightsLayer2Velocity[j] = (momentum * weightsLayer2Velocity[j]) + (learningRate * weightsLayer2Gradients[j]);
            weightsLayer2[j] += weightsLayer2Velocity[j];
        }
        
        // Update bias (no regularization for biases)
        biasLayer2Velocity = (momentum * biasLayer2Velocity) + (learningRate * outputError);
        biasLayer2 += biasLayer2Velocity;
        
        // Hidden layer error
        double[] hiddenError = new double[hiddenSize];
        for (int j = 0; j < hiddenSize; j++) {
            hiddenError[j] = outputError * weightsLayer2[j];
            // ReLU derivative: 1 if input > 0, 0 otherwise
            if (hidden[j] <= 0) {
                hiddenError[j] = 0;
            }
        }
        
        // Update hidden layer weights with regularization and momentum
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                double gradient = (hiddenError[j] * features[i]) - (l2Lambda * weightsLayer1[i][j]);
                weightsLayer1Velocity[i][j] = (momentum * weightsLayer1Velocity[i][j]) + (learningRate * gradient);
                weightsLayer1[i][j] += weightsLayer1Velocity[i][j];
            }
        }
        
        // Update hidden layer biases with momentum (no regularization)
        for (int j = 0; j < hiddenSize; j++) {
            biasesLayer1Velocity[j] = (momentum * biasesLayer1Velocity[j]) + (learningRate * hiddenError[j]);
            biasesLayer1[j] += biasesLayer1Velocity[j];
        }
    }
    
    /**
     * Computes values of hidden layer neurons using ReLU activation
     */
    private double[] computeHiddenLayer(double[] features) {
        double[] hidden = new double[hiddenSize];
        
        // Compute hidden layer values with ReLU activation
        for (int j = 0; j < hiddenSize; j++) {
            double sum = biasesLayer1[j];
            for (int i = 0; i < inputSize; i++) {
                sum += features[i] * weightsLayer1[i][j];
            }
            hidden[j] = Math.max(0, sum); // ReLU activation
        }
        
        return hidden;
    }
    
    /**
     * Computes raw output layer value (pre-activation)
     */
    private double computeOutputLayer(double[] hidden) {
        double output = biasLayer2;
        for (int j = 0; j < hiddenSize; j++) {
            output += hidden[j] * weightsLayer2[j];
        }
        return output;
    }
    
    /**
     * Sigmoid activation function
     */
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-Math.max(-20, Math.min(20, x)))); // Avoid overflow
    }
    
    /**
     * Updates tracking metrics for model training
     */
    private void updateTrainingMetrics(double loss) {
        trainingSteps++;
        // Exponential moving average of loss
        lastLoss = 0.9 * lastLoss + 0.1 * loss;
        
        // Log training progress periodically
        if (trainingSteps % 1000 == 0) {
            log.debug("Neural network training step {}: loss={}", 
                     trainingSteps, String.format("%.6f", lastLoss));
        }
    }
    
    /**
     * @return Latest training loss
     */
    public double getLastLoss() {
        return lastLoss;
    }
    
    /**
     * @return Number of training steps performed
     */
    public long getTrainingSteps() {
        return trainingSteps;
    }
    
    /**
     * Batch learning method to update network weights based on multiple examples at once
     * 
     * @param featuresBatch Array of input feature vectors
     * @param targetsBatch Array of target/actual values to learn
     * @param learningRate Learning rate for weight updates
     */
    public void learnBatch(double[][] featuresBatch, double[] targetsBatch, double learningRate) {
        if (featuresBatch.length != targetsBatch.length || featuresBatch.length == 0) {
            log.warn("Batch sizes don't match or empty batch provided");
            return;
        }
        
        int batchSize = featuresBatch.length;
        
        // Arrays to accumulate gradients
        double[][] weightsLayer1Gradients = new double[inputSize][hiddenSize];
        double[] biasesLayer1Gradients = new double[hiddenSize];
        double[] weightsLayer2Gradients = new double[hiddenSize];
        double biasLayer2Gradient = 0.0;
        
        double batchLoss = 0.0;
        
        // Compute gradients for each example
        for (int b = 0; b < batchSize; b++) {
            double[] features = featuresBatch[b];
            double target = targetsBatch[b];
            
            if (features.length != inputSize) {
                log.warn("Feature vector length {} doesn't match expected input size {}", 
                        features.length, inputSize);
                continue;
            }
            
            // Forward pass
            double[] hidden = computeHiddenLayer(features);
            double output = computeOutputLayer(hidden);
            double prediction = sigmoid(output);
            
            // Calculate loss
            batchLoss += Math.pow(target - prediction, 2);
            
            // Output layer error
            double outputError = (target - prediction) * prediction * (1 - prediction);
            
            // Accumulate gradients for output layer
            for (int j = 0; j < hiddenSize; j++) {
                weightsLayer2Gradients[j] += outputError * hidden[j];
            }
            biasLayer2Gradient += outputError;
            
            // Hidden layer error
            double[] hiddenError = new double[hiddenSize];
            for (int j = 0; j < hiddenSize; j++) {
                hiddenError[j] = outputError * weightsLayer2[j];
                // ReLU derivative: 1 if input > 0, 0 otherwise
                if (hidden[j] <= 0) {
                    hiddenError[j] = 0;
                }
            }
            
            // Accumulate gradients for hidden layer
            for (int i = 0; i < inputSize; i++) {
                for (int j = 0; j < hiddenSize; j++) {
                    weightsLayer1Gradients[i][j] += hiddenError[j] * features[i];
                }
            }
            
            for (int j = 0; j < hiddenSize; j++) {
                biasesLayer1Gradients[j] += hiddenError[j];
            }
        }
        
        // Add L2 regularization to gradients
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                weightsLayer1Gradients[i][j] -= l2Lambda * weightsLayer1[i][j];
            }
        }
        
        for (int j = 0; j < hiddenSize; j++) {
            weightsLayer2Gradients[j] -= l2Lambda * weightsLayer2[j];
        }
        
        // Calculate L2 regularization term for loss
        double l2Term = 0.0;
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                l2Term += weightsLayer1[i][j] * weightsLayer1[i][j];  
            }
        }
        for (int j = 0; j < hiddenSize; j++) {
            l2Term += weightsLayer2[j] * weightsLayer2[j];
        }
        
        // Add regularization term to batch loss
        batchLoss = (batchLoss / batchSize) + (l2Lambda * l2Term / 2.0);
        updateTrainingMetrics(batchLoss);
        
        // Update weights and biases with momentum based on average gradients
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                double avgGradient = weightsLayer1Gradients[i][j] / batchSize;
                weightsLayer1Velocity[i][j] = (momentum * weightsLayer1Velocity[i][j]) + 
                                            (learningRate * avgGradient);
                weightsLayer1[i][j] += weightsLayer1Velocity[i][j];
            }
        }
        
        for (int j = 0; j < hiddenSize; j++) {
            double avgGradient = biasesLayer1Gradients[j] / batchSize;
            biasesLayer1Velocity[j] = (momentum * biasesLayer1Velocity[j]) + 
                                    (learningRate * avgGradient);
            biasesLayer1[j] += biasesLayer1Velocity[j];
            
            avgGradient = weightsLayer2Gradients[j] / batchSize;
            weightsLayer2Velocity[j] = (momentum * weightsLayer2Velocity[j]) + 
                                     (learningRate * avgGradient);
            weightsLayer2[j] += weightsLayer2Velocity[j];
        }
        
        double avgGradient = biasLayer2Gradient / batchSize;
        biasLayer2Velocity = (momentum * biasLayer2Velocity) + (learningRate * avgGradient);
        biasLayer2 += biasLayer2Velocity;
        
        log.debug("Completed batch training with {} examples, loss={}", batchSize, 
                String.format("%.6f", batchLoss));
    }
    
    /**
     * Set momentum coefficient for faster convergence
     * @param momentum Value between 0 and 1 (higher = more momentum)
     */
    public void setMomentum(double momentum) {
        if (momentum < 0.0 || momentum > 1.0) {
            log.warn("Invalid momentum value {}, should be between 0 and 1", momentum);
            return;
        }
        this.momentum = momentum;
        log.debug("Set momentum to {}", momentum);
    }
    
    /**
     * Set L2 regularization strength to prevent overfitting
     * @param lambda Regularization strength (higher = more regularization)
     */
    public void setL2Regularization(double lambda) {
        if (lambda < 0.0) {
            log.warn("Invalid L2 regularization lambda {}, should be non-negative", lambda);
            return;
        }
        this.l2Lambda = lambda;
        log.debug("Set L2 regularization lambda to {}", lambda);
    }
    
    /**
     * Reset network weights and all training progress
     */
    public void reset() {
        // Xavier initialization for better convergence
        double scale = Math.sqrt(2.0 / (inputSize + hiddenSize));
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                weightsLayer1[i][j] = (random.nextDouble() * 2 - 1) * scale;
                weightsLayer1Velocity[i][j] = 0.0;
            }
        }
        
        for (int j = 0; j < hiddenSize; j++) {
            weightsLayer2[j] = (random.nextDouble() * 2 - 1) * scale;
            weightsLayer2Velocity[j] = 0.0;
            biasesLayer1[j] = 0.0;
            biasesLayer1Velocity[j] = 0.0;
        }
        
        biasLayer2 = 0.0;
        biasLayer2Velocity = 0.0;
        lastLoss = Double.MAX_VALUE;
        trainingSteps = 0;
        
        log.debug("Reset neural network weights and training progress");
    }
    
    /**
     * Saves the neural network model to a file
     * 
     * @param filepath File path to save the model
     * @return true if save was successful, false otherwise
     */
    public boolean saveModel(String filepath) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filepath))) {
            oos.writeObject(this);
            log.info("Neural network model saved to {}", filepath);
            return true;
        } catch (IOException e) {
            log.error("Failed to save neural network model: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Loads a neural network model from a file
     * 
     * @param filepath File path to load the model from
     * @return The loaded neural network model, or null if loading failed
     */
    public static SimpleNeuralNetwork loadModel(String filepath) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filepath))) {
            SimpleNeuralNetwork model = (SimpleNeuralNetwork) ois.readObject();
            log.info("Neural network model loaded from {}", filepath);
            return model;
        } catch (IOException | ClassNotFoundException e) {
            log.error("Failed to load neural network model: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Creates a deep copy of this neural network model
     * 
     * @return A deep copy of the model
     */
    public SimpleNeuralNetwork copy() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);
            oos.close();
            
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (SimpleNeuralNetwork) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.error("Failed to copy neural network model: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Trains the neural network on a batch of data for multiple iterations
     * 
     * @param inputs Array of input feature vectors
     * @param expectedOutputs Array of expected output values
     * @param iterations Number of training iterations to perform
     * @param learningRate Learning rate for weight updates
     * @return Final training loss after all iterations
     */
    public double train(double[][] inputs, double[][] expectedOutputs, int iterations, double learningRate) {
        if (inputs.length != expectedOutputs.length) {
            log.error("Input and output arrays must have the same length");
            return Double.MAX_VALUE;
        }
        
        if (inputs.length == 0) {
            log.warn("Cannot train on empty dataset");
            return Double.MAX_VALUE;
        }
        
        // Check input dimensions
        if (inputs[0].length != inputSize) {
            log.error("Input dimension {} doesn't match network input size {}", 
                    inputs[0].length, inputSize);
            return Double.MAX_VALUE;
        }
        
        // Flatten expected outputs if they're in 2D array format
        double[] flattenedOutputs = new double[expectedOutputs.length];
        for (int i = 0; i < expectedOutputs.length; i++) {
            flattenedOutputs[i] = expectedOutputs[i][0]; // Take first element of each output array
        }
        
        log.info("Starting batch training for {} iterations on {} examples", 
                iterations, inputs.length);
        
        double finalLoss = Double.MAX_VALUE;
        
        // Perform multiple iterations of batch training
        for (int iter = 0; iter < iterations; iter++) {
            learnBatch(inputs, flattenedOutputs, learningRate);
            finalLoss = getLastLoss();
            
            // Log progress periodically
            if (iter == 0 || iter == iterations-1 || (iter+1) % 100 == 0) {
                log.debug("Training iteration {}/{}: loss={}", 
                        iter+1, iterations, String.format("%.6f", finalLoss));
            }
            
            // Early stopping if loss is very low
            if (finalLoss < 0.001) {
                log.info("Early stopping at iteration {} with loss={}", 
                       iter+1, String.format("%.6f", finalLoss));
                break;
            }
        }
        
        log.info("Completed training with final loss={}", String.format("%.6f", finalLoss));
        return finalLoss;
    }
    
    /**
     * Returns the input size of the network
     */
    public int getInputSize() {
        return inputSize;
    }
    
    /**
     * Returns the hidden layer size of the network
     */
    public int getHiddenSize() {
        return hiddenSize;
    }
}
