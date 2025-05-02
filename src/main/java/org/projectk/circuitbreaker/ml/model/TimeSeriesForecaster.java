package org.projectk.circuitbreaker.ml.model;

import java.util.LinkedList;
import java.util.Queue;
import lombok.extern.slf4j.Slf4j;

/**
 * Simple time series forecasting model using AR (Auto-Regressive) and 
 * MA (Moving Average) components to capture temporal patterns in data.
 */
@Slf4j
public class TimeSeriesForecaster {
    private final double[] arCoefficients;  // AR coefficients
    private final double[] maCoefficients;  // MA coefficients
    private final Queue<Double> pastValues;  // Historical values for AR
    private final Queue<Double> pastErrors;  // Historical errors for MA
    private double lastForecast = 0.5;
    private long forecastCount = 0;
    
    /**
     * Creates a time series forecaster
     * 
     * @param arOrder Number of auto-regressive terms
     * @param maOrder Number of moving-average terms
     */
    public TimeSeriesForecaster(int arOrder, int maOrder) {
        this.arCoefficients = new double[arOrder];
        this.maCoefficients = new double[maOrder];
        this.pastValues = new LinkedList<>();
        this.pastErrors = new LinkedList<>();
        
        // Initialize AR coefficients with decaying weights
        for (int i = 0; i < arOrder; i++) {
            arCoefficients[i] = 0.5 / (i + 1);
        }
        
        // Initialize MA coefficients
        for (int i = 0; i < maOrder; i++) {
            maCoefficients[i] = 0.3 / (i + 1);
        }
        
        log.debug("Initialized TimeSeriesForecaster with arOrder={}, maOrder={}", 
                 arOrder, maOrder);
    }
    
    /**
     * Forecasts next value based on historical data
     * 
     * @return Forecasted value between 0 and 1
     */
    public double forecast() {
        if (pastValues.isEmpty()) {
            return 0.5; // Default if no history
        }
        
        double prediction = 0.0;
        double arSum = 0.0;
        double maSum = 0.0;
        
        // Apply AR component
        int i = 0;
        for (Double value : pastValues) {
            if (i >= arCoefficients.length) break;
            arSum += value * arCoefficients[i++];
        }
        
        // Apply MA component
        i = 0;
        for (Double error : pastErrors) {
            if (i >= maCoefficients.length) break;
            maSum += error * maCoefficients[i++];
        }
        
        // Combine components
        prediction = arSum + maSum;
        
        // Ensure prediction is in valid range
        prediction = Math.max(0.0, Math.min(1.0, prediction));
        lastForecast = prediction;
        forecastCount++;
        
        // Log occasional forecast info
        if (forecastCount % 100 == 0) {
            log.debug("Time series forecast #{}: predicted={}, arComponent={}, maComponent={}", 
                     forecastCount, String.format("%.4f", prediction),
                     String.format("%.4f", arSum), 
                     String.format("%.4f", maSum));
        }
        
        return prediction;
    }
    
    /**
     * Updates the model with a new actual value and recalibrates coefficients
     * 
     * @param actual The actual observed value
     */
    public void update(double actual) {
        // Forecast first to ensure we have lastForecast
        if (forecastCount == 0) {
            forecast();
        }
        
        double error = actual - lastForecast;
        
        // Update history
        pastValues.add(actual);
        pastErrors.add(error);
        
        // Maintain queue size
        if (pastValues.size() > arCoefficients.length) {
            pastValues.poll();
        }
        if (pastErrors.size() > maCoefficients.length) {
            pastErrors.poll();
        }
        
        // Update coefficients using simple gradient descent
        double learningRate = 0.01;
        
        // Update AR coefficients
        int i = 0;
        for (Double value : pastValues) {
            if (i >= arCoefficients.length) break;
            arCoefficients[i] += learningRate * error * value;
            i++;
        }
        
        // Update MA coefficients
        i = 0;
        for (Double pastError : pastErrors) {
            if (i >= maCoefficients.length) break;
            maCoefficients[i] += learningRate * error * pastError;
            i++;
        }
        
        // Periodically normalize coefficients to prevent instability
        if (forecastCount % 50 == 0) {
            normalizeCoefficients();
        }
    }
    
    /**
     * Normalizes coefficient weights to prevent instability
     */
    private void normalizeCoefficients() {
        // Normalize AR coefficients
        double arSum = 0.0;
        for (double coef : arCoefficients) {
            arSum += Math.abs(coef);
        }
        
        if (arSum > 0.95) {
            for (int i = 0; i < arCoefficients.length; i++) {
                arCoefficients[i] *= 0.95 / arSum;
            }
        }
        
        // Normalize MA coefficients
        double maSum = 0.0;
        for (double coef : maCoefficients) {
            maSum += Math.abs(coef);
        }
        
        if (maSum > 0.5) {
            for (int i = 0; i < maCoefficients.length; i++) {
                maCoefficients[i] *= 0.5 / maSum;
            }
        }
    }
    
    /**
     * @return Last forecast value
     */
    public double getLastForecast() {
        return lastForecast;
    }
    
    /**
     * @return Mean squared error of recent forecasts
     */
    public double getRecentMSE() {
        if (pastErrors.isEmpty()) {
            return 0.0;
        }
        
        double sumSquaredErrors = 0.0;
        for (double error : pastErrors) {
            sumSquaredErrors += error * error;
        }
        
        return sumSquaredErrors / pastErrors.size();
    }
}
