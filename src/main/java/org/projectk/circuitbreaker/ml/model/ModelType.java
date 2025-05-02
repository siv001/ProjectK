package org.projectk.circuitbreaker.ml.model;

/**
 * Enum representing types of neural network models that can be used
 * in the ML Circuit Breaker.
 */
public enum ModelType {
    /**
     * Classification model that outputs a binary decision
     */
    CLASSIFIER,
    
    /**
     * Regression model that outputs a continuous value
     */
    REGRESSOR,
    
    /**
     * Anomaly detection model that identifies unusual patterns
     */
    ANOMALY_DETECTOR
}
