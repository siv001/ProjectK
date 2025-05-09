# ML-Enhanced Circuit Breaker

This project implements an advanced circuit breaker pattern enhanced with machine learning capabilities for more intelligent failure detection and prevention in microservice architectures.

## Overview

The ML-Enhanced Circuit Breaker extends traditional circuit breakers by using machine learning to predict service failures before they occur. Unlike conventional circuit breakers that rely on fixed thresholds, this implementation learns from historical service behavior to make more accurate and proactive decisions.

### Key Features

- **Predictive Failure Detection**: Uses neural network model to predict potential service failures before they occur
- **Multi-metric Analysis**: Considers multiple factors (latency, error rate, system load, etc.) for decision making
- **Adaptive Learning**: Continuously learns and adapts to changing service behavior patterns
- **Dual Persistence**: Stores metrics in both file system and optionally in Redis TimeSeries for resilience and analysis
- **Configurable Behavior**: Extensive configuration options for tuning circuit breaker behavior

## Architecture

The ML-Enhanced Circuit Breaker consists of these main components:

1. **Core Circuit Breaker**: Coordinates the overall circuit breaking logic and decision process
2. **Neural Network Model**: Learns patterns and predicts service failures
3. **Metrics Collection**: Gathers and processes service performance metrics
4. **Persistence Layer**: Stores historical metrics for learning and analysis
5. **Configuration System**: Controls circuit breaker behavior and features

## Neural Network Implementation

### Network Architecture

The circuit breaker uses a feed-forward neural network with the following architecture:

- **Input Layer**: 4-5 neurons accepting normalized metrics (error rate, latency, system load, throughput, etc.)
- **Hidden Layer**: 8 neurons with sigmoid activation functions
- **Output Layer**: Single neuron with sigmoid activation producing a probability of service failure

```
                   [Hidden Layer]
                 /              \\
[Input Layer]   /                \\\   [Output Layer]
 Error Rate ----                  \\\\
 Latency    ----     [8 neurons]  ------ Failure Probability (0-1)
 System Load ----                  /////
 Throughput  ----                //
                 \\              /
                  \\____________/
```

### Feature Engineering

Raw service metrics are transformed into neural network inputs through the following process:

1. **Normalization**: All input features are scaled to a range of [0,1]
   - Error rate is already in this range (0-100%)
   - Latency is normalized using an exponential decay function: `1 - exp(-latency/threshold)`
   - System load is divided by a configurable maximum load factor
   - Throughput is normalized against historical peak values

2. **Temporal Features**: Metrics are aggregated over sliding time windows
   - Recent metrics are weighted more heavily than older ones
   - Rate-of-change is calculated to detect rapidly degrading services

3. **Feature Vector**: The final input vector combines these normalized values

### Training Process

The neural network is trained through a supervised learning approach:

1. **Training Data Generation**:
   - Historical service metrics are retrieved from the persistence layer
   - Service calls are labeled as failures if they resulted in errors or exceeded latency thresholds
   - A balanced dataset is created to prevent bias toward success cases

2. **Backpropagation Training**:
   - Standard gradient descent algorithm with configurable learning rate
   - MSE (Mean Squared Error) is used as the loss function
   - Training occurs periodically (default: every 5 minutes) and on startup using historical data
   - Early stopping is implemented to prevent overfitting

3. **Model Evaluation**:
   - Model is evaluated using separate validation data
   - Performance metrics tracked include precision, recall, and F1 score for failure prediction
   - If the new model performs worse than the existing one, training is rolled back

### Prediction and Decision Making

The circuit breaker makes decisions based on neural network predictions:

1. **Real-time Prediction**:
   - For each service call, current metrics are normalized and fed to the neural network
   - The network outputs a probability between 0 and 1 indicating failure likelihood
   - Higher values indicate higher probability of service degradation or failure

2. **Decision Threshold**:
   - The output probability is compared against a configurable threshold (default: 0.6)
   - Threshold can be dynamically adjusted based on service criticality

3. **Circuit State Management**:
   - When failure probability exceeds the threshold, the circuit transitions to OPEN state
   - In OPEN state, calls are immediately rejected (failing fast)
   - After a configurable timeout, circuit transitions to HALF_OPEN to test recovery
   - In HALF_OPEN state, a limited number of test calls are allowed
   - If test calls succeed with low failure probability, circuit returns to CLOSED state

4. **Feedback Loop**:
   - Actual outcomes of service calls are compared to predictions
   - This data is used in subsequent training cycles to improve prediction accuracy
   - The system becomes more accurate over time as it observes more failure patterns

### Hybrid Decision Making

The circuit breaker employs a hybrid approach that combines ML predictions with traditional metrics:

- **Emergency Thresholds**: Fixed thresholds can immediately open the circuit in extreme cases
- **Prediction Confidence**: For low-confidence predictions, more weight is given to traditional metrics
- **Cold Start Handling**: Traditional circuit breaking is used until enough data is collected for ML predictions

## Component Details

### Core Circuit Breaker Components

#### MLCircuitBreaker
**File**: `org.projectk.circuitbreaker.ml.MLCircuitBreaker`
**Responsibility**: Main implementation of the ML-enhanced circuit breaker. Coordinates metric collection, prediction, and circuit state management.

This class:
- Tracks the state of the circuit (OPEN, CLOSED, HALF_OPEN)
- Collects service metrics from each call
- Uses the neural network to predict potential failures
- Controls traffic based on circuit state and predictions
- Schedules periodic model training based on collected metrics

#### CircuitBreakerState
**File**: `org.projectk.circuitbreaker.ml.CircuitBreakerState`
**Responsibility**: Enum representing possible states of the circuit breaker (OPEN, CLOSED, HALF_OPEN) with state transition logic.

### Machine Learning Components

#### SimpleNeuralNetwork
**File**: `org.projectk.circuitbreaker.ml.model.SimpleNeuralNetwork`
**Responsibility**: Implements a feed-forward neural network that learns to predict service failures based on performance metrics.

This class:
- Implements a 3-layer neural network architecture
- Provides forward propagation for predictions
- Implements backpropagation for training
- Includes methods for model serialization and deserialization

#### ServiceMetric
**File**: `org.projectk.circuitbreaker.ml.ServiceMetric`
**Responsibility**: Data class representing a single service call metric data point.

Stores individual metrics for each service call including:
- Timestamp
- Response latency
- Success/failure status
- System load at time of call
- Concurrency level

#### MetricSnapshot
**File**: `org.projectk.circuitbreaker.ml.MetricSnapshot`
**Responsibility**: Aggregates service metrics over a time window for model training and prediction.

This class:
- Holds a collection of individual service metrics
- Calculates aggregate statistics (error rate, average latency, etc.)
- Prepares features for the neural network

### Persistence Layer

#### MetricsPersistenceProvider (Interface)
**File**: `org.projectk.circuitbreaker.ml.persistence.MetricsPersistenceProvider`
**Responsibility**: Defines the contract for metrics persistence implementations.

Key methods:
- `storeMetrics()`: Saves metrics to the persistence store
- `loadHistoricalMetrics()`: Retrieves historical metrics for model training
- `shutdown()`: Clean shutdown of persistence resources

#### CompositeMetricsPersistence
**File**: `org.projectk.circuitbreaker.ml.persistence.CompositeMetricsPersistence`
**Responsibility**: Delegates to multiple persistence providers, enabling both file and Redis storage.

This class:
- Maintains a collection of persistence providers
- Delegates operations to all registered providers
- Handles provider failures gracefully

#### FileBasedMetricsPersistence
**File**: `org.projectk.circuitbreaker.ml.persistence.FileBasedMetricsPersistence`
**Responsibility**: Implements file-based storage for metrics using JSON serialization.

This class:
- Stores metrics in JSON files organized by date
- Implements periodic flushing to optimize I/O
- Manages file cleanup based on retention policy
- Provides the primary metrics persistence mechanism

#### MetricsPersistenceService
**File**: `org.projectk.circuitbreaker.ml.persistence.MetricsPersistenceService`
**Responsibility**: Implements database storage for metrics using JDBC.

This class:
- Stores metrics in a relational database (H2 by default)
- Manages database schema creation and updates
- Implements periodic batch processing for performance
- Handles data retention and cleanup

#### RedisTimeSeriesService
**File**: `org.projectk.circuitbreaker.ml.persistence.redis.RedisTimeSeriesService`
**Responsibility**: Optional Redis TimeSeries-based persistence for high-performance time-series data storage.

This class:
- Uses Redis TimeSeries for efficient time-series storage
- Stores metrics with automatic expiration/retention
- Provides fast query capabilities for time-range data
- Loads conditionally only when Redis is available

#### EmbeddedRedisConfig
**File**: `org.projectk.circuitbreaker.ml.persistence.redis.EmbeddedRedisConfig`
**Responsibility**: Provides an embedded Redis server for development and testing.

This class:
- Configures and starts an embedded Redis instance
- Manages the lifecycle of the Redis container
- Integrates with Spring configuration system
- Loads conditionally based on profile and dependencies

### Support Classes

#### TimeSeriesPoint
**File**: `org.projectk.circuitbreaker.ml.persistence.redis.TimeSeriesPoint`
**Responsibility**: Data class for representing individual time-series data points in Redis.

#### RedisTimeSeriesCommands
**File**: `org.projectk.circuitbreaker.ml.persistence.redis.RedisTimeSeriesCommands`
**Responsibility**: Utility class for generating Redis TimeSeries commands.

#### PersistedMetric
**File**: `org.projectk.circuitbreaker.ml.persistence.PersistedMetric`
**Responsibility**: Data class representing metrics in a format optimized for persistence.

## Configuration

The ML-Enhanced Circuit Breaker can be configured using Spring configuration properties:

### Circuit Breaker Configuration

```yaml
circuit-breaker:
  ml:
    enabled: true                    # Enable ML-based circuit breaking
    window-size-ms: 60000            # Time window for metrics collection (1 minute)
    min-call-count: 10               # Minimum calls before ML prediction activates
    failure-threshold: 0.5           # Error rate threshold for conventional breaking
    half-open-calls: 3               # Number of test calls in HALF_OPEN state
    reset-timeout-ms: 30000          # How long to wait before attempting recovery
    training-interval-ms: 300000     # How often to re-train the model (5 minutes)
```

### Metrics Persistence Configuration

```yaml
metrics:
  file:
    enabled: true                    # Enable file-based persistence (default)
    base-path: ./metrics-data        # Directory to store metrics files
    flush-interval-ms: 60000         # Flush interval (60 seconds)
    cleanup-interval-ms: 3600000     # Cleanup interval (1 hour)
    retention-days: 30               # Keep metrics for 30 days

  # Redis TimeSeries persistence configuration (optional)
  redis:
    enabled: false                   # Disabled by default
    host: localhost                  # Redis server host
    port: 6379                       # Redis server port
    retention-days: 30               # Keep metrics for 30 days
    cleanup-interval-ms: 3600000     # Cleanup interval (1 hour)
    # Embedded Redis configuration (for development/testing)
    embedded:
      enabled: false                 # Disabled by default
```

## Usage

### Basic Usage

```java
@Autowired
private MLCircuitBreaker circuitBreaker;

public Response callService() {
    return circuitBreaker.executeWithFallback(
        "serviceA",                           // service name
        () -> actualServiceCall(),           // service call lambda
        () -> fallbackResponse()             // fallback lambda
    );
}
```

### Advanced Usage with Metric Customization

```java
@Autowired
private MLCircuitBreaker circuitBreaker;

public Response callServiceAdvanced() {
    return circuitBreaker.executeWithCustomMetrics(
        "serviceB",                           // service name
        () -> actualServiceCall(),           // service call lambda
        () -> fallbackResponse(),            // fallback lambda
        metrics -> {
            metrics.setSystemLoad(getCustomLoadMetric());
            return metrics;
        }
    );
}
```

## Extension Points

The ML-Enhanced Circuit Breaker is designed to be extensible:

1. **Custom ML Models**: Replace SimpleNeuralNetwork with more advanced models
2. **Additional Persistence Providers**: Implement MetricsPersistenceProvider interface
3. **Custom Metrics Collection**: Extend MetricSnapshot for additional metrics
4. **Integration with Monitoring Systems**: Implement health reporting interfaces

## Dependencies

- Spring Boot 3.x
- Java 17 or higher
- H2 Database (for metrics persistence)
- Redis (optional, for TimeSeries storage)
- TestContainers (optional, for embedded Redis)

lsof -i :8080 | grep LISTEN | awk '{print $2}' | xargs -r kill -9