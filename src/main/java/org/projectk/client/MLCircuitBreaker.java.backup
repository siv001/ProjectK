package org.projectk.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MLCircuitBreaker {
    private final String name;
    private final MetricsCollector metricsCollector;
    private final ThresholdPredictor predictor;
    private final MLPerformanceMonitor performanceMonitor;
    private final AnomalyDetector anomalyDetector;
    private final AdaptiveConfigManager configManager;
    private volatile CircuitBreaker circuitBreaker;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public MLCircuitBreaker(
            @Value("${circuit.breaker.name:defaultBreaker}") String name,
            MeterRegistry meterRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.name = name;
        this.metricsCollector = new MetricsCollector();
        this.predictor = new ThresholdPredictor();
        this.performanceMonitor = new MLPerformanceMonitor(meterRegistry);
        this.anomalyDetector = new AnomalyDetector();
        this.configManager = new AdaptiveConfigManager(predictor);
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.circuitBreaker = createCircuitBreaker();
    }

    private CircuitBreaker createCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(predictor.getOptimalWindowSize())
                .failureRateThreshold(predictor.getCurrentThreshold())
                .waitDurationInOpenState(predictor.getOptimalWaitDuration())
                .build();

        return circuitBreakerRegistry.circuitBreaker(name, config);
    }

    private void updateCircuitBreakerConfig(CircuitBreakerConfig newConfig) {
        try {
            // Store the current state
            CircuitBreaker.State currentState = circuitBreaker.getState();

            // Remove existing circuit breaker
            circuitBreakerRegistry.remove(name);

            // Create new circuit breaker with updated config
            circuitBreaker = circuitBreakerRegistry.circuitBreaker(name, newConfig);

            // Restore previous state if needed
            if (currentState == CircuitBreaker.State.OPEN) {
                circuitBreaker.transitionToOpenState();
            } else if (currentState == CircuitBreaker.State.HALF_OPEN) {
                circuitBreaker.transitionToHalfOpenState();
            }

            log.info("Circuit breaker configuration updated for {}", name);
        } catch (Exception e) {
            log.error("Failed to update circuit breaker configuration", e);
        }
    }

    public <T> Mono<T> executeWithML(Supplier<Mono<T>> operation) {
        return Mono.fromSupplier(() -> {
            long startTime = System.nanoTime();
            MetricSnapshot snapshot = metricsCollector.getCurrentMetrics();

            if (!anomalyDetector.isAnomaly(snapshot)) {
                predictor.updateThresholds(snapshot);
                CircuitBreakerConfig newConfig = configManager.getUpdatedConfig(snapshot);
                updateCircuitBreakerConfig(newConfig);
            }

            return circuitBreaker.decorateSupplier(() -> {
                try {
                    T result = operation.get().block();
                    recordSuccess(startTime);
                    return result;
                } catch (Exception e) {
                    recordFailure(startTime, e);
                    throw e;
                }
            }).get();
        });
    }

    private void recordSuccess(long startTime) {
        long latency = System.nanoTime() - startTime;
        ServiceMetric metric = new ServiceMetric(
            System.currentTimeMillis(),
            latency,
            true,
            circuitBreaker.getMetrics().getNumberOfBufferedCalls(),
            getSystemLoad()
        );
        metricsCollector.recordMetric(metric);
        performanceMonitor.recordPredictionAccuracy(true, predictor.getLastPrediction());
    }

    private void recordFailure(long startTime, Exception e) {
        long latency = System.nanoTime() - startTime;
        ServiceMetric metric = new ServiceMetric(
            System.currentTimeMillis(),
            latency,
            false,
            circuitBreaker.getMetrics().getNumberOfBufferedCalls(),
            getSystemLoad()
        );
        metricsCollector.recordMetric(metric);
        performanceMonitor.recordPredictionAccuracy(false, predictor.getLastPrediction());
        log.error("Circuit breaker operation failed", e);
    }

    private double getSystemLoad() {
        return ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    }

    @Data
    private static class ServiceMetric {
        private final long timestamp;
        private final long latency;
        private final boolean success;
        private final int concurrentCalls;
        private final double systemLoad;
    }

    private static class MetricsCollector {
        private final Queue<ServiceMetric> metrics = new ConcurrentLinkedQueue<>();
        private static final int WINDOW_SIZE = 1000;

        public void recordMetric(ServiceMetric metric) {
            metrics.offer(metric);
            if (metrics.size() > WINDOW_SIZE) {
                metrics.poll();
            }
        }

        public MetricSnapshot getCurrentMetrics() {
            return new MetricSnapshot(new ArrayList<>(metrics));
        }
    }

    private static class ThresholdPredictor {
        private final OnlineGradientDescent predictor;
        private double lastPrediction;
        private static final double LEARNING_RATE = 0.01;

        public ThresholdPredictor() {
            this.predictor = new OnlineGradientDescent(LEARNING_RATE);
        }

        public void updateThresholds(MetricSnapshot metrics) {
            double[] features = extractFeatures(metrics);
            lastPrediction = predictor.predict(features);
            double[] actual = calculateActualOutcomes(metrics);
            predictor.learn(features, actual);
        }

        public double getLastPrediction() {
            return lastPrediction;
        }

        public int getOptimalWindowSize() {
            return 100; // Initial default, will be adjusted by ML
        }

        public float getCurrentThreshold() {
            return 50.0f; // Initial default, will be adjusted by ML
        }

        public Duration getOptimalWaitDuration() {
            return Duration.ofSeconds(30); // Initial default, will be adjusted by ML
        }

        private double[] extractFeatures(MetricSnapshot metrics) {
            return new double[] {
                metrics.getP95Latency(),
                metrics.getErrorRate(),
                metrics.getConcurrency(),
                metrics.getSystemLoad(),
                metrics.getTimeOfDay()
            };
        }

        private double[] calculateActualOutcomes(MetricSnapshot metrics) {
            // Implementation depends on your specific success criteria
            return new double[] { metrics.getSuccessRate() };
        }
    }

    private static class OnlineGradientDescent {
        private double[] weights;
        private final double learningRate;

        public OnlineGradientDescent(double learningRate) {
            this.learningRate = learningRate;
            this.weights = new double[5]; // Number of features
        }

        public double predict(double[] features) {
            double prediction = 0.0;
            for (int i = 0; i < features.length; i++) {
                prediction += weights[i] * features[i];
            }
            return sigmoid(prediction);
        }

        public void learn(double[] features, double[] actual) {
            double predicted = predict(features);
            double error = actual[0] - predicted;

            for (int i = 0; i < weights.length; i++) {
                weights[i] += learningRate * error * features[i];
            }
        }

        private double sigmoid(double x) {
            return 1.0 / (1.0 + Math.exp(-x));
        }
    }

    private static class MLPerformanceMonitor {
        private final MeterRegistry registry;

        public MLPerformanceMonitor(MeterRegistry registry) {
            this.registry = registry;
        }

        public void recordPredictionAccuracy(boolean actual, double predicted) {
            double error = Math.abs(actual ? 1.0 : 0.0 - predicted);
            registry.gauge("ml.prediction.error", error);
        }

        public void recordModelMetrics(MetricSnapshot metrics) {
            registry.gauge("ml.feature.latency", metrics.getP95Latency());
            registry.gauge("ml.feature.error_rate", metrics.getErrorRate());
            registry.gauge("ml.feature.concurrency", metrics.getConcurrency());
        }
    }

    private static class AnomalyDetector {
        private static final double ANOMALY_THRESHOLD = -0.5;

        public boolean isAnomaly(MetricSnapshot metrics) {
            double[] features = extractFeatures(metrics);
            return calculateAnomalyScore(features) < ANOMALY_THRESHOLD;
        }

        private double[] extractFeatures(MetricSnapshot metrics) {
            return new double[] {
                metrics.getP95Latency(),
                metrics.getErrorRate(),
                metrics.getConcurrency(),
                metrics.getSystemLoad()
            };
        }

        private double calculateAnomalyScore(double[] features) {
            // Simple implementation - can be replaced with more sophisticated algorithms
            double sum = 0;
            for (double feature : features) {
                sum += feature;
            }
            return sum / features.length;
        }
    }

    private static class AdaptiveConfigManager {
        private final ThresholdPredictor predictor;

        public AdaptiveConfigManager(ThresholdPredictor predictor) {
            this.predictor = predictor;
        }

        public CircuitBreakerConfig getUpdatedConfig(MetricSnapshot metrics) {
            return CircuitBreakerConfig.custom()
                .failureRateThreshold(predictor.getCurrentThreshold())
                .slowCallRateThreshold(50.0f)
                .waitDurationInOpenState(predictor.getOptimalWaitDuration())
                .slidingWindowSize(predictor.getOptimalWindowSize())
                .build();
        }
    }

    @Data
    public static class MetricSnapshot {
        private final List<ServiceMetric> metrics;

        public double getP95Latency() {
            return calculateP95Latency();
        }

        public double getErrorRate() {
            return calculateErrorRate();
        }

        public double getSuccessRate() {
            return 1.0 - getErrorRate();
        }

        public double getConcurrency() {
            return metrics.stream()
                .mapToInt(ServiceMetric::getConcurrentCalls)
                .average()
                .orElse(0.0);
        }

        public double getSystemLoad() {
            return metrics.stream()
                .mapToDouble(ServiceMetric::getSystemLoad)
                .average()
                .orElse(0.0);
        }

        public double getTimeOfDay() {
            return LocalDateTime.now().getHour() / 24.0;
        }

        private double calculateP95Latency() {
            List<Long> latencies = metrics.stream()
                .map(ServiceMetric::getLatency)
                .sorted()
                .collect(Collectors.toList());

            int index = (int) Math.ceil(0.95 * latencies.size()) - 1;
            return latencies.get(Math.max(0, index));
        }

        private double calculateErrorRate() {
            long failures = metrics.stream()
                .filter(m -> !m.isSuccess())
                .count();
            return (double) failures / metrics.size();
        }
    }
}
