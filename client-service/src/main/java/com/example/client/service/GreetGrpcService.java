package com.example.client.service;

import com.example.client.grpc.GreeterGrpc;
import com.example.client.grpc.HelloRequest;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

/**
 * Calls storage-service over gRPC. The stub is the stand-in for the remote service:
 * stub.sayHello(request) looks like a normal method call, but it sends the request
 * over the channel and waits for the answer.
 */
@Service
public class GreetGrpcService {

    private final GreeterGrpc.GreeterBlockingStub stub;
    private final long deadlineMs;

    public GreetGrpcService(ManagedChannel storageChannel,
                            @Value("${storage.grpc.deadline-ms}") long deadlineMs) {
        this.stub = GreeterGrpc.newBlockingStub(storageChannel);   // cheap object bound to the channel
        this.deadlineMs = deadlineMs;
    }

    public String greet(String name) {
        try {
            return stub
                    .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)   // never wait forever
                    .sayHello(HelloRequest.newBuilder().setName(name).build())
                    .getMessage();
        } catch (StatusRuntimeException e) {
            // Turn the gRPC status into the HTTP status our own callers should see
            Status status = e.getStatus();
            String description = status.getDescription() == null ? "" : ": " + status.getDescription();
            throw new ResponseStatusException(
                    toHttpStatus(status.getCode()),
                    "storage-service gRPC call failed (" + status.getCode() + description + ")");
        }
    }

    private HttpStatus toHttpStatus(Status.Code code) {
        return switch (code) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;        // like 400
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;   // like 504
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;     // like 503
            default -> HttpStatus.BAD_GATEWAY;                      // anything else from the upstream service
        };
    }
}
