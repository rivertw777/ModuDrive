package com.moduDrive.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnErrorEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnFailureRateExceededEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
class CircuitBreakerEventConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    public void registerCircuitBreakerEventListeners() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> {
            circuitBreaker.getEventPublisher()
                    .onStateTransition(this::logStateTransition)
                    .onFailureRateExceeded(this::logFailureRateExceeded)
                    .onError(this::logErrorEvent);
        });
    }

    private void logStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        log.info("CircuitBreaker '{}' state changed from {} to {}",
                event.getCircuitBreakerName(),
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState());
    }

    private void logFailureRateExceeded(CircuitBreakerOnFailureRateExceededEvent event) {
        log.warn("CircuitBreaker '{}' failure rate exceeded: {}%",
                event.getCircuitBreakerName(),
                event.getFailureRate());
    }

    private void logErrorEvent(CircuitBreakerOnErrorEvent event) {
        log.error("CircuitBreaker '{}' recorded an error: {}",
                event.getCircuitBreakerName(),
                event.getThrowable().getMessage());
    }
}