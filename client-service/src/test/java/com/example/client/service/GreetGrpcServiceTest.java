package com.example.client.service;

import com.example.client.grpc.GreeterGrpc;
import com.example.client.grpc.HelloReply;
import com.example.client.grpc.HelloRequest;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Plain unit test: no Spring context and no running storage-service.
// A small fake Greeter server runs in-process and the real generated stub talks to it.
class GreetGrpcServiceTest {

    private Server server;
    private ManagedChannel channel;
    private GreetGrpcService service;

    /** Fake storage-service: greets, or answers INVALID_ARGUMENT for a blank name. */
    private static class FakeGreeter extends GreeterGrpc.GreeterImplBase {
        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloReply> out) {
            if (request.getName().isBlank()) {
                out.onError(Status.INVALID_ARGUMENT.withDescription("name is empty").asRuntimeException());
                return;
            }
            out.onNext(HelloReply.newBuilder().setMessage("Hello, " + request.getName()).build());
            out.onCompleted();
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new FakeGreeter())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        service = new GreetGrpcService(channel, 2000);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void greetReturnsAnswerFromStorageService() {
        assertEquals("Hello, Ada", service.greet("Ada"));
    }

    @Test
    void blankNameBecomesBadRequest() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.greet(" "));

        assertEquals(HttpStatus.BAD_REQUEST.value(), e.getStatusCode().value());
    }

    @Test
    void unreachableStorageServiceBecomesServiceUnavailable() {
        // a channel to a server that does not exist: the call fails fast with UNAVAILABLE
        ManagedChannel deadChannel = InProcessChannelBuilder.forName("no-such-server").directExecutor().build();
        GreetGrpcService brokenService = new GreetGrpcService(deadChannel, 2000);

        try {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> brokenService.greet("Ada"));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), e.getStatusCode().value());
        } finally {
            deadChannel.shutdownNow();
        }
    }
}
