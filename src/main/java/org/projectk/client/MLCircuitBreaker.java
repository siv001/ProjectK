package org.projectk.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * Wrapper for the ML-enhanced Circuit Breaker implementation.
 * This class delegates to the refactored implementation in org.projectk.circuitbreaker.ml package.
 * This maintains backward compatibility while organizing the code more effectively.
 */
@Slf4j
@Component
public class MLCircuitBreaker {

    private final org.projectk.circuitbreaker.ml.MLCircuitBreaker delegate;

    @Autowired
    public MLCircuitBreaker(@Qualifier("mlCircuitBreakerImpl") org.projectk.circuitbreaker.ml.MLCircuitBreaker delegate) {
        log.info("Initializing MLCircuitBreaker wrapper using mlCircuitBreakerImpl bean");
        this.delegate = delegate;
    }

    /**
     * Executes an operation with ML-enhanced circuit breaking
     *
     * @param operation The operation to execute
     * @return Mono with the operation result
     */
    public <T> Mono<T> executeWithML(Supplier<Mono<T>> operation) {
        return delegate.executeWithML(operation);
    }
}
