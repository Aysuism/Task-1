package com.example.storage.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Starts the gRPC server next to the normal Spring web server (port 8082)
 * and stops it when the application shuts down.
 */
@Component
public class GrpcServer {

    private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

    private final Server server;

    public GrpcServer(GreeterGrpcService greeterService,
                      @Value("${grpc.server.port}") int port) {
        this.server = ServerBuilder.forPort(port)     // which port to listen on
                .addService(greeterService)           // routes /greeter.v1.Greeter/SayHello to sayHello
                .build();
    }

    @PostConstruct
    public void start() throws IOException {
        server.start();                               // opens the port, accepts calls
        log.info("gRPC server started on port {}", server.getPort());
    }

    @PreDestroy
    public void stop() {
        server.shutdown();
    }
}
