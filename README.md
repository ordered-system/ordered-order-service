# ordered-order-service

[![CI](https://github.com/ordered-system/ordered-order-service/actions/workflows/ci.yml/badge.svg)](https://github.com/ordered-system/ordered-order-service/actions/workflows/ci.yml)

Orders and payments bounded context for [ordered-system](https://github.com/ordered-system), extracted from the [`ordered-backend`](https://github.com/ordered-system/ordered-backend) monolith via the Strangler Fig pattern. Owns its own PostgreSQL database — no other service touches `order_db` directly.

## What it does

- **Order placement**, orchestrated by `OrderPlacementOrchestrator`: reserves stock on `product-service` (via a synchronous checkout-reservation call), charges the card through Stripe, and persists the order — with the database transaction and the external Stripe call deliberately kept in separate steps, so a slow/failed payment provider can never hold a DB transaction open.
- **Payments** via Stripe, wrapped in Resilience4j `@CircuitBreaker` + `@Retry` (the fallback fires on the `@Retry` annotation, not the circuit breaker, due to how Resilience4j orders its aspects — worth knowing if you're debugging why a fallback isn't triggering when you expect). Failed payments are picked up by `PaymentRetryScheduler`, which polls every 5 minutes for up to 5 attempts before cancelling the order.
- **Delivery address snapshotting** — the address on an `Order` is a point-in-time copy (`DeliveryAddress`), not a live reference to the buyer's address book, so a later address edit never rewrites history on an already-placed order.
- **Outbox pattern for Kafka**: state changes (e.g. `order-delivered`) are written to an `OutboxEvent` table in the same transaction as the business change, then published to Kafka by a scheduled poller (`@EnableScheduling` is required here — its absence was an early bug that silently stopped events from ever going out) — giving at-least-once delivery without a distributed transaction.

## API

Base path `/api/v1/orders` and `/api/v1/payments`, reached through [`ordered-gateway`](https://github.com/ordered-system/ordered-gateway) in the full system. OpenAPI docs at `/v3/api-docs` (or via the gateway's aggregated Swagger UI).

## Stack

Java 21 · Spring Boot 4.1.0 · PostgreSQL + Flyway (`ddl-auto: validate` — schema changes only happen through migrations, never Hibernate auto-DDL) · Kafka (outbox pattern) · Stripe + Resilience4j · Eureka Client · Spring Cloud Config Client · Micrometer / Prometheus / OpenTelemetry tracing · [`ordered-commons`](https://github.com/ordered-system/ordered-commons) for JWT claims auth and shared exception handling

## Running it locally

Needs [`ordered-eureka`](https://github.com/ordered-system/ordered-eureka), [`ordered-config-server`](https://github.com/ordered-system/ordered-config-server), Kafka, and its own Postgres. The easiest path is running the whole system via [`ordered-infra`](https://github.com/ordered-system/ordered-infra) — but to run just this service in isolation:

```bash
git clone https://github.com/ordered-system/ordered-commons.git
(cd ordered-commons && make install)     # ordered-commons isn't on Maven Central

git clone https://github.com/ordered-system/ordered-order-service.git
cd ordered-order-service
make up                                   # starts this service's own Postgres on :5433
STRIPE_SECRET_KEY=sk_test_... make run    # spring-boot:run, needs a real Stripe test key for payments to work
```

Runs on **port 9091**.

### Docker

The `Dockerfile` expects `ordered-commons` as an additional build context (see that repo's README) — building this service standalone with plain `docker build .` will fail on the `ordered-commons` dependency. Use `ordered-infra`'s compose files, which wire the context correctly, or add `--build-context commons=../ordered-commons` yourself.

## Testing

```bash
make test-unit          # unit tests only
make test-integration   # unit + integration tests (needs Docker — Testcontainers spins up real Postgres)
```

Integration tests use Testcontainers inline (`@ServiceConnection` on a `static PostgreSQLContainer`, no shared base class) and live in the parent package (e.g. `pl.dybcio.ordered.payment`), while unit tests sit in `.service` subpackages — `StripePaymentIntegrationTest` and `PaymentRetrySchedulerTest` are good starting points for seeing the payment flow end-to-end.

## Where this fits

| Service | Database | Role |
|---|---|---|
| **ordered-order-service** | PostgreSQL | Orders, cart checkout, payments (Stripe) |
| [ordered-product-service](https://github.com/ordered-system/ordered-product-service) | PostgreSQL + Redis | Product catalog, stock reservation |
| [ordered-user-service](https://github.com/ordered-system/ordered-user-service) | PostgreSQL | Users, auth, JWT issuance |
| [ordered-engagement-service](https://github.com/ordered-system/ordered-engagement-service) | MongoDB | Reviews, browsing history |

Part of the [ordered-system](https://github.com/ordered-system) organization — decomposed from [`ordered-backend`](https://github.com/ordered-system/ordered-backend), load-tested in [`ordered-load-tests`](https://github.com/ordered-system/ordered-load-tests).

## License

MIT — see [LICENSE](LICENSE).
