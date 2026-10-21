package com.example.storage.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

/**
 * Server logic for the Greeter service: plain Java, no networking.
 * The generated GreeterImplBase (the "skeleton") receives the bytes, unpacks them
 * into a HelloRequest and calls this method.
 *
 * The reply text matches the REST endpoint GET /api/storage/greet/{name}
 * so both transports can be compared on the same call.
 */
@Component
public class GreeterGrpcService extends GreeterGrpc.GreeterImplBase {

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> out) {
        String name = request.getName();   // proto3 strings are never null; unset = ""

        // The caller's mistake -> INVALID_ARGUMENT (like HTTP 400)
        if (name.isBlank()) {
            out.onError(Status.INVALID_ARGUMENT
                    .withDescription("name is empty")
                    .asRuntimeException());
            return;
        }

        HelloReply reply = HelloReply.newBuilder()
                .setMessage("Hello, " + name + "! Greetings from storage-service.")
                .build();
        out.onNext(reply);     // send the answer
        out.onCompleted();     // always finish the call
    }
}
