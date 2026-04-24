# BancoLuso | Real-Time Payment Status API

Spring Boot 3 service that replaces a nightly batch reconciliation job with a real-time REST API. Built as part of the Adentis technical challenge.

---

## Stack

Java 17 · Spring Boot 3.2 · PostgreSQL · Apache Kafka · Docker · Flyway · Testcontainers

---

## How to Run

You need Docker. That's it.

    docker compose up --build

Postgres and Kafka start first. The app waits for both to be healthy before starting.
Flyway runs the migration automatically on startup.

Check it's up:

    curl http://localhost:8080/actuator/health

Swagger UI: http://localhost:8080/swagger-ui.html

---

## Quick Test

Send a payment event:

    curl -X POST http://localhost:8080/v1/payments/events \
      -H "Content-Type: application/json" \
      -d '{
        "referenceId": "TXN-2026-0001234",
        "amount": 1500.00,
        "currency": "EUR",
        "debtorName": "Mohamed khabir",
        "debtorIban": "PT50000201231234567890154",
        "creditorIban": "PT50000201239876543210154",
        "valueDate": "2026-04-14",
        "status": "PENDING",
        "eventTimestamp": "2026-04-14T09:23:00Z"
      }'

Wait a second then query:

    curl http://localhost:8080/v1/payments/TXN-2026-0001234/status

List all payments:

    curl "http://localhost:8080/v1/payments?page=0&size=10"

---

## Run Tests

    mvn test

Tests use real Postgres and Kafka containers via Testcontainers — no mocks.
Docker must be running.

---

## Why I built it this way

The brief mentioned 85,000 payments per business day. That's not steady load —
it comes in bursts, mostly morning and end of day.

A synchronous DB write on every POST would tie response time directly to DB
throughput. Under burst load that gets ugly fast. Putting Kafka between the HTTP
layer and the DB write keeps the endpoint fast regardless of what the DB is doing.
If the consumer goes down, events sit in Kafka and get processed when it recovers.
Nothing is lost.

I picked Kafka over RabbitMQ specifically because of message retention. RabbitMQ
drops messages the moment they're consumed. In a banking context you need to be
able to replay events for auditing or debugging — that ruled RabbitMQ out early.

The trade-off I'm not fully happy with: a client that POSTs and immediately GETs
might get a 404 for a second while the consumer is still processing. The 202
response makes this contract explicit, but it's something a real client would need
to handle. I'd solve this properly with a polling mechanism or a webhook on status
change.

### Idempotency

The consumer takes a pessimistic write lock (SELECT FOR UPDATE) before deciding
what to do with an incoming event. This prevents two consumer instances from
processing the same referenceId simultaneously during a Kafka rebalance.

The rules I landed on:
- New referenceId → insert everything
- Same referenceId, same status → ignore, true duplicate
- Same referenceId, different status, newer timestamp → update status only
- Same referenceId, different status, older timestamp → ignore, stale event

Financial fields (amount, IBANs, currency) are immutable after first ingestion.
Only status and eventTimestamp can ever change. I thought about allowing
corrections but decided that's a separate flow — amendments should be explicit,
not silent overwrites.

### Kafka message key = referenceId

All events for the same payment go to the same partition, so they're always
processed in order. This eliminates a whole class of race conditions before the
consumer code even runs. Simple decision but it matters.

### Flyway over ddl-auto

ddl-auto: update is fine locally. In a banking context schema changes need to be
versioned, reviewable, and reproducible across environments. Flyway makes that
the default rather than something you remember to do later.

---

## What's missing and why it matters

**Dead-letter queue** — right now a consumer failure just gets logged and the
offset still moves. That means a bad event disappears silently. A DLQ topic or
a payment_errors table would let failed events be inspected and replayed. This
is the thing I'd add first.

**Outbox pattern** — there's a small window between the HTTP layer accepting the
event and Kafka actually receiving it. If the producer fails in that window, the
event is gone. A transactional outbox with Debezium CDC would close that gap
properly. I didn't implement it here because it adds significant infrastructure
complexity, but in a real bank I wouldn't ship without it.

**Auth** — nothing is secured right now. The ingest endpoint needs mTLS or an
API key at minimum, the query endpoint should require a JWT. Skipped it to keep
the challenge focused on the core problem.

**Schema Registry** — JSON works but doesn't enforce contracts between producer
and consumer. A schema change on the producer can silently break the consumer.
Avro or Protobuf with Confluent Schema Registry is the right move for anything
beyond a single team.

**Distributed tracing** — logs already include referenceId as an MDC field which
helps a lot when debugging. But a proper OpenTelemetry trace spanning HTTP, Kafka,
and the consumer would make production incidents much faster to diagnose.

---

## Endpoints

| Method | Path                              | Description                  |
|--------|-----------------------------------|------------------------------|
| POST   | /v1/payments/events               | Ingest a payment event       |
| GET    | /v1/payments/{referenceId}/status | Get current payment status   |
| GET    | /v1/payments                      | List all payments (paginated)|
| GET    | /actuator/health                  | Health check                 |
| GET    | /actuator/prometheus              | Metrics                      |
| GET    | /swagger-ui.html                  | API docs                     |

---

Mohamed Khabir |
khabir.mohamed12@gmail.com | +216 93 831 879 |
linkedin.com/in/mohamedkhabir | github.com/mohamedkhabir
