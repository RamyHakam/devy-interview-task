# Idempotent Quote-to-Order Core

Java 21 / Spring Boot 3 service for price-locked quotes, exactly-once confirmation, stock-safe order creation, an auditable approval gate, and human review.

## Approach

- Modular boundaries: HTTP API, application service, domain policy, persistence, and observability packages.
- Quotes snapshot unit prices and line totals at creation; expiry is fixed at 15 minutes.
- Confirmation serializes a given idempotency key with a PostgreSQL row lock. The request hash rejects key reuse with a changed payload.
- A unique constraint on `orders.quote_id` is a second line of defense against duplicate conversion of one quote.
- Stock is decremented using a conditional SQL update (`stock_quantity >= requested`) in the same transaction. PostgreSQL row-level write locking prevents overselling across competing quotes.
- Policy is a pure domain function: threshold or credit-limit breach means `PENDING_REVIEW`; otherwise `APPROVED`.
- Audit records capture actor, action, inputs, result, and timestamp. A database trigger rejects UPDATE and DELETE, not merely an application convention.
- Metrics are exposed through Actuator (`/actuator/metrics` and `/actuator/prometheus`); `/actuator/health` is the health endpoint.

## Trade-offs

The service is designed to be carved out incrementally rather than via a big-bang cutover.
1. Start by importing customer, SKU, price, and stock reference data from the legacy CRM.
2. Run quote creation in shadow mode while the CRM remains authoritative.
3. Compare generated totals and expiry decisions against legacy quotes and reconcile discrepancies.
4. Route a small, explicitly selected sales cohort to this service for new quotes.
5. Keep a stable quote/order identifier mapping in the integration layer.
6. During the pilot, publish completed orders back to the CRM through an adapter.
7. Expand traffic by customer segment or product family, with a feature-flagged rollback to legacy quote creation.
8. Move order confirmation ownership only after stock and financial reconciliation is proven.
9. Use an outbox table and stream publisher next so order events are delivered reliably.
10. Add alerting for stock conflicts, approval backlog, latency, and idempotency anomalies.
11. Add per-actor/customer rate limits, authentication, authorization, and secrets management.
12. Add reconciliation jobs, operational dashboards, retention policies, and disaster-recovery exercises.

## AI usage

No LLM is called. AI agents are ordinary API clients and identify themselves through `actorId` on confirmation. The actor is recorded in the audit trail. Human review requires a reviewer name and reason. Any AI-generated recommendation remains subject to the deterministic policy gate.

## How to run

Requirements: Java 21, Maven 3.9+, and Docker.

```bash
docker compose up -d postgres
mvn test
mvn spring-boot:run
```

The integration tests use Testcontainers and start an isolated real PostgreSQL instance; Docker must be available. The application uses the Compose PostgreSQL database by default.

Create a quote:

```bash
curl -s http://localhost:8080/quotes -H 'Content-Type: application/json' \
  -d '{"customerId":1,"items":[{"sku":"STEEL-PLATE-10","quantity":2}]}'
```

Confirm it (replace the UUID):

```bash
curl -s http://localhost:8080/quotes/QUOTE_UUID/confirm \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: sales-req-001' \
  -d '{"actorId":"ai-agent:devy"}'
```

Review an order:

```bash
curl -s http://localhost:8080/orders/ORDER_UUID/review -H 'Content-Type: application/json' \
  -d '{"decision":"approve","reviewer":"jane.doe","reason":"Credit and pricing verified"}'
```

Read its audit history at `GET /orders/{id}/audit`. Seed customers are IDs `1` and `2`; seeded SKUs are `STEEL-PLATE-10`, `COPPER-WIRE-5`, and `BOLT-M12`. Configure `APPROVAL_THRESHOLD`, `DB_URL`, `DB_USER`, and `DB_PASSWORD` through environment variables.

## Scope exclusions

Authentication/authorization, UI, payments, logistics, external CRM integration, and real AI/LLM calls are intentionally not implemented. In production, actor identity must be authenticated rather than trusted from request bodies.
