package com.example.client.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcConfig {

    /**
     * ONE channel to storage-service, shared by the whole app.
     * A channel manages the connection: it connects on the first call, reconnects if the
     * connection drops, and carries many calls over one HTTP/2 connection.
     * Creating a channel per request would be a costly mistake.
     */
    @Bean(destroyMethod = "shutdown")
    public ManagedChannel storageChannel(@Value("${storage.grpc.host}") String host,
                                         @Value("${storage.grpc.port}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()   // no encryption: fine inside this local setup, use TLS in production
                .build();
    }
}
