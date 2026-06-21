EVENT LEDGER

A two-service Spring Boot system that ingests financial transaction events, enforces idempotency, supports out-of-order delivery, and maintains correct account balances. The system is designed to behave predictably when the internal Account Service is unavailable and to make each request traceable across both services.

ARCHITECTURE OVERVIEW

Client or curl
    |
    v
Event Gateway Service, port 8080
    |
    | REST call with X-Trace-Id propagated
    v
Account Service, port 8081

The system has two independently runnable services.

Event Gateway Service, port 8080:
The public entry point. It validates incoming events, enforces idempotency on eventId, stores event records in its own database, calls the Account Service to apply transactions, exposes event read endpoints from its own database, and provides a balance proxy endpoint.

Account Service, port 8081:
The internal service. It owns account state, stores transactions, enforces idempotency on eventId, and computes balances. It is called by the Gateway and is not intended to be exposed directly to external clients.

HOW THE SERVICES INTERACT

A POST /events request flows from the Gateway to the Account Service synchronously over REST.

1. The Gateway validates the event payload.
2. The Gateway checks whether the eventId already exists locally.
3. If the event is new, the Gateway calls POST /accounts/{accountId}/transactions on the Account Service.
4. The Gateway saves the event in its own database only after the Account Service call succeeds.
5. The Account Service stores the transaction idempotently and uses it for balance calculation.

Read endpoints such as GET /events/{id} and GET /events?account=... are served from the Gateway database and do not call the Account Service.

WHY EACH SERVICE HAS ITS OWN H2 DATABASE

Each service has its own in-memory H2 database. The Gateway uses gatewaydb and the Account Service uses accountdb. The services never share a database or in-process state.

This keeps the service boundary clear. The services communicate only through REST APIs, not through a shared database schema. It also allows the Gateway read endpoints to continue working even when the Account Service is down.

The trade-off is that event information exists in two places: as an event in the Gateway and as a transaction in the Account Service. To avoid orphaned Gateway records, the Gateway calls the Account Service first and saves the local event only after the downstream call succeeds.

PREREQUISITES

Java 17
Maven 3.9 or later
Docker and Docker Compose are optional. They are only needed for the Docker Compose run path.

No external database or message broker is required. Both services use embedded H2.

HOW TO RUN

Use two terminal windows.

Terminal 1: Run Account Service

    cd account-service
    mvn spring-boot:run

Terminal 2: Run Event Gateway

    cd event-gateway-service
    mvn spring-boot:run

DOCKER COMPOSE RUN OPTION

As an alternative to running both services manually, you can start both services with Docker Compose from the repository root.

Build and start both containers:

    docker compose up --build

This builds an image for each service and starts both containers on the same Docker network.

Inside Docker, the Gateway does not call the Account Service through localhost. The docker-compose.yml file sets:

    ACCOUNT_SERVICE_BASE_URL=http://account-service:8081

Spring Boot maps this environment variable to the account.service.base-url property. This lets the Gateway reach the Account Service by its Docker Compose service name.

Stop and remove the containers:

    docker compose down

Health checks:

    curl http://localhost:8081/health
    curl http://localhost:8080/health

Expected examples:

    {"service":"account-service","status":"UP","database":"UP"}
    {"service":"event-gateway-service","status":"UP","database":"UP"}

HOW TO RUN TESTS

Run tests per service.

Account Service:

    cd account-service
    mvn test

Event Gateway:

    cd ../event-gateway-service
    mvn test

The Account Service tests cover balance calculation, idempotency, validation, and out-of-order ordering.

The Gateway tests cover invalid events returning 400, successful event submission, duplicate handling, Account Service failure returning 503, Gateway read endpoints continuing to work when the Account Service is unavailable, and trace ID propagation. A separate integration test verifies the real Gateway to Account Service HTTP flow using WireMock as the downstream Account Service stand-in.

SAMPLE CURL COMMANDS

Submit a CREDIT event:

    curl -i -X POST http://localhost:8080/events \
      -H "Content-Type: application/json" \
      -d '{"eventId":"evt-001","accountId":"acct-123","type":"CREDIT","amount":150.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z","metadata":{"source":"mainframe-batch","batchId":"B-9042"}}'

Submit a DEBIT event:

    curl -i -X POST http://localhost:8080/events \
      -H "Content-Type: application/json" \
      -d '{"eventId":"evt-002","accountId":"acct-123","type":"DEBIT","amount":50.00,"currency":"USD","eventTimestamp":"2026-05-15T15:00:00Z"}'

Submit the same event again. This is idempotent and does not double-count:

    curl -i -X POST http://localhost:8080/events \
      -H "Content-Type: application/json" \
      -d '{"eventId":"evt-001","accountId":"acct-123","type":"CREDIT","amount":150.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}'

Get a single event by ID:

    curl http://localhost:8080/events/evt-001

List events for an account, ordered by event timestamp:

    curl "http://localhost:8080/events?account=acct-123"

Get balance through the Gateway proxy:

    curl http://localhost:8080/accounts/acct-123/balance

Submit an event with a custom trace ID:

    curl -i -X POST http://localhost:8080/events \
      -H "Content-Type: application/json" \
      -H "X-Trace-Id: trace-abc-123" \
      -d '{"eventId":"evt-003","accountId":"acct-123","type":"CREDIT","amount":25.00,"currency":"USD","eventTimestamp":"2026-05-15T16:00:00Z"}'

View metrics:

    curl http://localhost:8080/metrics
    curl http://localhost:8081/metrics

DESIGN DECISIONS

Idempotency

eventId is the idempotency key in both services.

In the Gateway, eventId is the primary key of the event table. If the same eventId is submitted again, the Gateway returns the existing event with 200 OK and does not call the Account Service again. A first-time submission returns 201 Created.

In the Account Service, eventId has a uniqueness rule and is checked before inserting a transaction. If a duplicate eventId arrives, the existing transaction is returned and the balance is not changed.

This guarantees that submitting the same event multiple times does not create duplicate transactions and does not alter the balance.

Out-of-Order Handling

Events may arrive in any order. Arrival order is not used for correctness.

Event listings are returned ordered by eventTimestamp. Balance is computed by summing all transactions for the account, regardless of the order in which they arrived.

Each event also stores receivedAt for diagnostics, but receivedAt is not used for ordering or balance logic.

Balance Calculation

Balance is calculated as:

    sum of CREDIT amounts minus sum of DEBIT amounts

The Account Service computes balance from the stored transactions for the account. BigDecimal is used for money values to avoid floating-point rounding issues.

Distributed Tracing with X-Trace-Id

Each request is traceable using the X-Trace-Id header.

The Gateway checks for X-Trace-Id on incoming requests. If the header is missing, the Gateway generates a UUID. The Gateway returns the trace ID in the response header, includes it in Gateway logs, and forwards it to the Account Service.

The Account Service reads the same X-Trace-Id header, includes it in its logs, and returns it in its response header.

This makes it possible to follow one request across both services by searching for the same trace ID in both logs.

Resiliency: Timeout, Retry, and Backoff

Gateway calls to the Account Service use timeout and retry behavior.

The HTTP client has connect and read timeouts so the Gateway does not hang indefinitely if the Account Service is slow or unavailable.

Failed Account Service calls are retried up to a small maximum number of attempts. A short backoff is used between attempts.

If all attempts fail, the Gateway returns 503 Service Unavailable instead of returning a generic 500 error.

Graceful Degradation

When the Account Service is unavailable, the Gateway degrades predictably.

POST /events returns 503 Service Unavailable after retries are exhausted.

GET /events/{id} continues to work because it reads from the Gateway database.

GET /events?account=... continues to work because it reads from the Gateway database.

GET /accounts/{accountId}/balance returns 503 because balance is owned by the Account Service and cannot be answered locally by the Gateway.

The Gateway saves an event locally only after the Account Service call succeeds, so a failed downstream call does not leave an orphaned Gateway event.

Observability

Both services include JSON-style structured logs with service name, trace ID, and event labels.

Both services expose health endpoints at GET /health.

Both services expose simple in-memory metrics at GET /metrics.

The Gateway tracks event request counts, duplicate event counts, balance request counts, and downstream failures.

The Account Service tracks transaction requests, applied transactions, duplicates, and balance requests.

KNOWN LIMITATIONS

Both services use in-memory H2 databases. Data is lost when services restart.

Currency conversion is not implemented. The currency field is stored and validated, but balances are not separated or converted by currency.

Metrics are in-memory and reset on restart.

Metrics are not exposed in Prometheus format.

Logging is JSON-style string logging, not a full structured logging encoder.

Authentication and authorization are not implemented.

Retry behavior uses a fixed small number of attempts and simple backoff. It does not include jitter or a circuit breaker.

FUTURE IMPROVEMENTS

Add OpenTelemetry with Jaeger or Zipkin for visual distributed traces.

Expose Prometheus-format metrics and latency histograms.

Add a circuit breaker to fail fast during sustained Account Service outages.

Add exponential backoff with jitter.

Use a durable database instead of in-memory H2.

Add schema migration using Flyway or Liquibase.

Add an asynchronous fallback that queues events when the Account Service is down and replays them after recovery.

Add authentication, authorization, and rate limiting for public Gateway endpoints.

Add Docker Compose for easier local startup.
