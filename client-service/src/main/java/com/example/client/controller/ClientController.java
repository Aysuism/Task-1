package com.example.client.controller;

import com.example.client.feign.StorageClient;
import com.example.client.service.GreetGrpcService;
import com.example.client.service.StorageGuardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/client")
public class ClientController {

    private final StorageClient storageClient;
    private final StorageGuardService guardService;
    private final GreetGrpcService greetGrpcService;

    public ClientController(StorageClient storageClient,
                            StorageGuardService guardService,
                            GreetGrpcService greetGrpcService) {
        this.storageClient = storageClient;
        this.guardService = guardService;
        this.greetGrpcService = greetGrpcService;
    }

    @GetMapping("/ping-storage")
    public Map<String, Object> pingStorage() {
        Map<String, Object> storageAnswer = storageClient.ping();

        return Map.of(
                "service", "client-service",
                "storageAnswer", storageAnswer
        );
    }

    @GetMapping("/ping-storage-safe")
    public Map<String, Object> pingStorageSafe() {
        return Map.of(
                "service", "client-service",
                "storageAnswer", guardService.ping()
        );
    }

    @GetMapping("/greet/{name}")
    public String greet(@PathVariable("name") String name) {
        return storageClient.greet(name);
    }

    // Same greeting as /greet/{name}, but over gRPC instead of REST + Feign
    @GetMapping("/greet-grpc/{name}")
    public String greetGrpc(@PathVariable("name") String name) {
        return greetGrpcService.greet(name);
    }
}