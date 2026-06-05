package com.moduDrive.common.infrastructure.resilience4j;

import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
class RetryEventConfig {

    private final RetryRegistry retryRegistry;

    @PostConstruct
    public void registerRetryEventListeners() {
        retryRegistry.getAllRetries().forEach(retry -> retry.getEventPublisher()
                .onRetry(this::logRetry));
    }

    private void logRetry(RetryOnRetryEvent event) {
        log.info("Retry for '{}' attempt number: {}",
                event.getName(),
                event.getNumberOfRetryAttempts());
    }
}