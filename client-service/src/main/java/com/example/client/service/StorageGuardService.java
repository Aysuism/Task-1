package com.example.client.service;

import com.example.client.feign.StorageClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StorageGuardService {

    private final StorageClient storageClient;

    public StorageGuardService(StorageClient storageClient) {
        this.storageClient = storageClient;
    }

    @CircuitBreaker(name = "storage", fallbackMethod = "pingFallback")
    public Map<String, Object> ping() {
        return storageClient.ping();
    }

    // Runs when the breaker is OPEN or the call failed
    private Map<String, Object> pingFallback(Throwable t) {
        return Map.of(
                "status", "DOWN",
                "message", "storage-service is unavailable, please try again later",
                "reason", t.getClass().getSimpleName()
        );
    }
}