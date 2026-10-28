# Task-1: client-service + storage-service + email-service

Three small Spring Boot microservices. client-service and storage-service talk to each other over HTTP (REST + Feign) and over gRPC. When storage-service saves a message it also publishes an event to RabbitMQ, and email-service consumes it and sends an email (see [Event-driven email notifications](#event-driven-email-notifications-rabbitmq)).

- **client-service** (port `8081`) is the public entry point. It validates and processes incoming messages, then calls storage-service through a Feign client. It can also call storage-service over gRPC.
- **storage-service** (port `8082`, gRPC port `9090`) saves and returns messages using PostgreSQL.
- **email-service** (port `8083`) listens to a RabbitMQ queue and sends an email for each saved message.
- **PostgreSQL 16**, **RabbitMQ** and **Mailpit** (a fake SMTP server with a web inbox) run in Docker.

```
 curl / browser
       |
       v
 client-service :8081  --(Feign, HTTP/JSON)-->  storage-service :8082  --(JPA)-->  PostgreSQL :5433
                       --(gRPC, HTTP/2)------>  storage-service :9090
```

## Tech stack

- Java 17, Maven (use the included `mvnw` / `mvnw.cmd`)
- Spring Boot 3.3.5
- client-service: Spring Web, Spring Cloud OpenFeign (Spring Cloud 2023.0.3), Resilience4j circuit breaker, Actuator, gRPC client (grpc-java 1.68.0)
- storage-service: Spring Web, Spring Data JPA, PostgreSQL driver, gRPC server (grpc-java 1.68.0)
- Protocol Buffers 3.25.5 (generated at build time by `protobuf-maven-plugin`)
- Docker and Docker Compose

## Project structure

```
.
├── docker-compose.yml          # postgres + rabbitmq + mailpit + storage-service + client-service + email-service
├── screenshots/                # proof that the email flow works (see the last section)
├── email-service/              # RabbitMQ consumer + email sender (see the last section)
├── client-service/
│   ├── Dockerfile              # multi-stage build (Maven -> JRE 17)
│   ├── pom.xml
│   └── src/main/
│       ├── proto/greeter.proto                # gRPC contract (copy, keep identical to storage-service)
│       └── java/com/example/client/
│           ├── ClientServiceApplication.java   # @SpringBootApplication + @EnableFeignClients
│           ├── controller/
│           │   ├── ClientController.java       # /api/client/*  (ping, greet, greet-grpc)
│           │   └── MessageController.java      # /api/messages  (create, list)
│           ├── service/
│           │   ├── MessageProcessingService.java  # validates + cleans message, then stores it
│           │   ├── StorageGuardService.java       # circuit breaker around the storage ping
│           │   └── GreetGrpcService.java          # gRPC stub call with a deadline, maps gRPC status to HTTP status
│           ├── feign/StorageClient.java        # Feign interface describing storage-service's API
│           ├── config/
│           │   ├── FeignConfig.java            # Feign retry settings
│           │   └── GrpcConfig.java             # the one shared gRPC ManagedChannel
│           └── dto/                            # MessageRequest, ProcessedMessage, StoredMessage (records)
└── storage-service/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/
        ├── proto/greeter.proto                 # gRPC contract (copy, keep identical to client-service)
        └── java/com/example/storage/
            ├── StorageServiceApplication.java
            ├── controller/
            │   ├── StorageController.java      # /api/storage/ping, /api/storage/greet/{name}
            │   └── MessageController.java      # /api/storage/messages (save, list)
            ├── grpc/
            │   ├── GreeterGrpcService.java     # gRPC server logic (extends the generated GreeterImplBase)
            │   └── GrpcServer.java             # starts/stops the gRPC server with the Spring app
            ├── service/MessageService.java     # transactional save / findAll
            ├── repository/MessageRepository.java  # Spring Data JPA
            ├── entity/Message.java             # table "messages"
            └── dto/                            # SaveMessageRequest, MessageResponse (records)
```

Each service has its own `application.yml` in `src/main/resources`.

## How to run

### Option A: everything in Docker

```bash
docker compose up --build
```

Starts Postgres, storage-service and client-service. Open `http://localhost:8081`.

### Option B: services from the IDE or Maven (Postgres in Docker)

```bash
docker compose up postgres                      # database only
cd storage-service && ./mvnw spring-boot:run    # terminal 2 (Windows: mvnw.cmd)
cd client-service  && ./mvnw spring-boot:run    # terminal 3
```

Start storage-service before client-service. The default settings in `application.yml` already point to `localhost`.

The first build runs `protoc` through `protobuf-maven-plugin`, so it needs internet access to download `protoc` and the gRPC plugin. The generated classes are written to `target/generated-sources/protobuf/`.

### Configuration (environment variables)

| Variable                                 | Used by         | Default                                     |
|------------------------------------------|-----------------|---------------------------------------------|
| `DB_HOST`, `DB_PORT`                     | storage-service | `localhost`, `5433`                         |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD`      | storage-service | `storagedb`, `storage_user`, `storage_pass` |
| `GRPC_SERVER_PORT`                       | storage-service | `9090`                                      |
| `STORAGE_SERVICE_URL`                    | client-service  | `http://localhost:8082`                     |
| `STORAGE_GRPC_HOST`, `STORAGE_GRPC_PORT` | client-service  | `localhost`, `9090`                         |
| `STORAGE_GRPC_DEADLINE_MS`               | client-service  | `2000`                                      |

Inside Docker Compose, the database is reached at `postgres:5432`, storage-service over REST at `http://storage-service:8082` and over gRPC at `storage-service:9090`.

## API

### client-service (`:8081`)

| Method | Path                                            | Description                                                                                                                                                           |
|--------|-------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| POST   | `/api/messages`                                 | Body `{"author": "...", "text": "..."}`. Validates, processes, stores. Returns `201` with the stored message. Returns `400` if `author` or `text` is missing or blank |
| GET    | `/api/messages`                                 | List all stored messages                                                                                                                                              |
| GET    | `/api/client/ping-storage`                      | Calls storage-service ping directly (fails if storage is down)                                                                                                        |
| GET    | `/api/client/ping-storage-safe`                 | Same ping, wrapped in a circuit breaker with a fallback `DOWN` response                                                                                               |
| GET    | `/api/client/greet/{name}`                      | Returns the greeting from storage-service (REST + Feign)                                                                                                              |
| GET    | `/api/client/greet-grpc/{name}`                 | Returns the same greeting from storage-service over gRPC. Blank name gives `400`, storage down gives `503`, deadline exceeded gives `504`                             |
| GET    | `/actuator/health`, `/actuator/circuitbreakers` | Health and circuit breaker state                                                                                                                                      |

**Message processing** (`MessageProcessingService`): the author is trimmed and capitalized (`aDA` becomes `Ada`), the text is trimmed and repeated whitespace is collapsed to one space, `wordCount` is calculated, and `processedAt` is set to the current time.

### storage-service (`:8082`)

| Method | Path                        | Description                                                                                                                                     |
|--------|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| POST   | `/api/storage/messages`     | Save a processed message (`author`, `email`, `text`, `wordCount`, `processedAt`). Returns `201` and publishes a `MessageSavedEvent` to RabbitMQ |
| GET    | `/api/storage/messages`     | List all messages                                                                                                                               |
| GET    | `/api/storage/ping`         | Returns service name, status and time                                                                                                           |
| GET    | `/api/storage/greet/{name}` | Returns a greeting string                                                                                                                       |

### storage-service gRPC (`:9090`)

| Service              | Method                                                       | Description                                                                                      |
|----------------------|--------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `greeter.v1.Greeter` | `SayHello(HelloRequest{name}) returns (HelloReply{message})` | Same greeting as `GET /api/storage/greet/{name}`. Blank `name` returns status `INVALID_ARGUMENT` |

### Quick test

```bash
curl -X POST http://localhost:8081/api/messages \
  -H "Content-Type: application/json" \
  -d '{"author":"ada","text":"hello   from   the client"}'

curl http://localhost:8081/api/messages
curl http://localhost:8081/api/client/ping-storage-safe

# same greeting, two transports
curl http://localhost:8081/api/client/greet/Ada
curl http://localhost:8081/api/client/greet-grpc/Ada
curl -i "http://localhost:8081/api/client/greet-grpc/%20"      # blank name -> 400
```

Optional, call storage-service's gRPC port directly with [grpcurl](https://github.com/fullstorydev/grpcurl) (no server reflection, so pass the proto file):

```bash
grpcurl -plaintext -import-path storage-service/src/main/proto -proto greeter.proto \
  -d '{"name":"Ada"}' localhost:9090 greeter.v1.Greeter/SayHello
```

## Data model

Table `messages` (created automatically by Hibernate, `ddl-auto: update`):

| Column         | Type          | Notes                              |
|----------------|---------------|------------------------------------|
| `id`           | bigint        | generated identity                 |
| `author`       | varchar(100)  | not null                           |
| `text`         | varchar(1000) | not null                           |
| `word_count`   | int           | not null                           |
| `processed_at` | timestamp     | set by client-service              |
| `saved_at`     | timestamp     | set by storage-service when saving |

## Resilience settings (client-service)

- **Feign timeouts:** connect and read timeout 5 s.
- **Feign retries:** `FeignConfig` uses `Retryer.Default(2000, 5000, 3)`, meaning up to 3 attempts with backoff starting at 2 s.
- **Circuit breaker `storage`:** sliding window 10 calls, minimum 5 calls, opens at 50% failures, stays open 10 s, allows 3 trial calls when half-open. Currently applied only to `StorageGuardService.ping()`.
- **gRPC deadline:** every gRPC call has a 2 s deadline (`STORAGE_GRPC_DEADLINE_MS`). When it runs out, the call fails with `DEADLINE_EXCEEDED` and the endpoint returns `504`. The gRPC call is not behind the circuit breaker yet.

## Tests

```bash
./mvnw test        # run inside each service folder
```

- email-service: `MessageEventListenerTest` (sends an email for a valid event, rejects an invalid address). storage-service: `MessageEventPublisherTest` (publishes the right data, does not throw when RabbitMQ is down).
- Each of client-service and storage-service has a `contextLoads` test. The storage-service one needs the Postgres container running. It starts the gRPC server on a free port (`grpc.server.port=0`), so it does not clash with a running service on 9090.
- storage-service: `GreeterGrpcServiceTest` (success and empty name) uses the in-process gRPC transport. No Postgres and no ports needed.
- client-service: `GreetGrpcServiceTest` (success, blank name gives 400, unreachable server gives 503) runs the real stub against a fake in-process server.

## Conventions (for humans and AI assistants)

- Java 17, Spring Boot 3.3.5. Keep Spring and Spring Cloud versions as they are in the `pom.xml` files. Keep the gRPC and protobuf versions as a matching pair.
- Layered structure: controller → service → repository / Feign client / gRPC stub. Controllers stay thin.
- DTOs are Java `record`s. Entities are not exposed directly (storage-service maps `Message` to `MessageResponse`).
- Constructor injection, no field `@Autowired`.
- client-service never touches the database. All persistence goes through storage-service.
- The two services share no code. The DTOs and the `greeter.proto` file are duplicated on purpose, so keep JSON field names and proto field numbers identical on both sides. Never change or reuse a proto field number.
- Do not hard-code new hosts, ports or credentials. Add an `application.yml` property with an environment-variable default, as the existing config does.
- Generated gRPC code lives in `target/` and is never edited or committed.

## Current task

Practice task from `rpc_grpc_explained.pdf` (section 12), adapted to this repo: add gRPC next to the existing REST + Feign link instead of a separate project.

- storage-service exposes a `Greeter` gRPC service on port 9090 (`GreeterGrpcService`, `GrpcServer`).
- client-service calls it through one shared channel and a blocking stub with a 2 s deadline (`GrpcConfig`, `GreetGrpcService`) and exposes `GET /api/client/greet-grpc/{name}`.
- Empty name: gRPC `INVALID_ARGUMENT` on the service side, HTTP `400` at client-service.
- Tests: one success and one empty-name test on the storage side, plus success, blank name and unreachable-server tests on the client side.
- Done when: `/api/client/greet/Ada` and `/api/client/greet-grpc/Ada` both return a greeting, a blank name gives a clear error, no call can hang, and `./mvnw test` passes in both services.


## Event-driven email notifications (RabbitMQ)

When storage-service saves a message (`POST /api/storage/messages`), it also publishes a `MessageSavedEvent` to RabbitMQ. **email-service** consumes the event and sends an email to the message's `email` address. client-service never talks to email-service.

```
client-service --(Feign)--> storage-service --(AMQP)--> RabbitMQ --(AMQP)--> email-service --(SMTP)--> mailbox
                                  |                    exchange message.events                         (Mailpit locally)
                                  v                    queue    email.notifications
                              PostgreSQL               DLQ      email.notifications.dlq
```

- **Publisher** (`storage-service/.../messaging`): `MessageController` calls `MessageEventPublisher` after `MessageService.save` has committed. `MessageService` is unchanged. If RabbitMQ is down, the error is logged and the save still succeeds (`201`).
- **Message** (JSON): `eventId`, `messageId`, `author`, `recipientEmail`, `text`, `wordCount`, `savedAt`. Each service has its own copy of the record; only the field names are shared.
- **Queue**: durable exchange `message.events`, routing key `message.saved`, durable queue `email.notifications`, persistent messages. Both services declare the same topology (identical queue arguments), so start order does not matter.
- **Errors and retries** (email-service): acknowledge mode `auto` (ack when the listener returns). If sending fails, the listener retries 3 times (2 s, then 4 s). After that the message is rejected without requeue and RabbitMQ moves it to the dead-letter queue `email.notifications.dlq`. Look at it in the management UI.
- **email-service** (port `8083`) has no dependency on the other two services' code or HTTP APIs.

### Try it

```bash
docker compose up --build
curl -X POST http://localhost:8081/api/messages \
  -H "Content-Type: application/json" \
  -d '{"author":"ada","email":"ada@example.com","text":"hello from the client"}'
```

- Mailpit (fake inbox with the email): http://localhost:8025
- RabbitMQ management UI: http://localhost:15672 (guest / guest)
- To test the failure path: `docker compose stop mailpit`, send a message, and watch the message land in `email.notifications.dlq` after the retries.

| Variable                                         | Used by                        | Default                       |
|--------------------------------------------------|--------------------------------|-------------------------------|
| `RABBITMQ_HOST`, `RABBITMQ_PORT`                 | storage-service, email-service | `localhost`, `5672`           |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD`             | storage-service, email-service | `guest`, `guest`              |
| `MAIL_HOST`, `MAIL_PORT`                         | email-service                  | `localhost`, `1025`           |
| `MAIL_USER`, `MAIL_PASSWORD`                     | email-service                  | empty (no auth)               |
| `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`                | email-service                  | `false`, `false`              |
| `EMAIL_FROM`                                     | email-service                  | `noreply@example.com`         |

For a real SMTP server (for example Gmail with an app password) set `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true`.

### Notes

- Emails go to **Mailpit**, a local test SMTP server, so nothing is sent to real mailboxes. Open http://localhost:8025 to read them. To use a real SMTP server, set the `MAIL_*` variables above.
- The entity `Message` needs a no-argument constructor (`protected Message() {}`) so JPA can read rows from the database.

## Screenshots

All screenshots are in the `screenshots/` folder.

| # | File                        | What it shows                                                          |
|---|-----------------------------|------------------------------------------------------------------------|
| 1 | `01-docker-compose-ps.png`  | All containers running                                                 |
| 2 | `02-postman-post.png`       | `POST /api/messages` returns `201`                                     |
| 3 | `03-rabbitmq-queues.png`    | Queues `email.notifications` and `email.notifications.dlq`, 1 consumer |
| 4 | `04-email-service-logs.png` | email-service received the event and sent the email                    |
| 5 | `05-mailpit-inbox.png`      | The email in Mailpit                                                   |
| 6 | `06-failure-retry-logs.png` | SMTP down: retries in the email-service log                            |
| 7 | `07-rabbitmq-dlq.png`       | The failed message in the dead-letter queue                            |