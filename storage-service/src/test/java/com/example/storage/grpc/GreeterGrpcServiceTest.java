package com.example.storage.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Plain unit test: no Spring context, no Postgres, no ports.
// Client and server talk inside this JVM, but through the real generated gRPC code.
class GreeterGrpcServiceTest {

    private Server server;
    private ManagedChannel channel;
    private GreeterGrpc.GreeterBlockingStub stub;

    @BeforeEach
    void setUp() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new GreeterGrpcService())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = GreeterGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void sayHelloReturnsGreeting() {
        HelloReply reply = stub.sayHello(HelloRequest.newBuilder().setName("Ada").build());

        assertEquals("Hello, Ada! Greetings from storage-service.", reply.getMessage());
    }

    @Test
    void emptyNameReturnsInvalidArgument() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.sayHello(HelloRequest.newBuilder().setName("").build()));

        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
    }
}
