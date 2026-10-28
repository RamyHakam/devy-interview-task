# Fast and Correct Spend Report API

## Approach

Kotlin 2.2 + Spring Boot 3.5 + PostgreSQL 17. Money is stored exclusively as `BIGINT` minor units.

The report endpoint uses plain SQL with dynamic, whitelisted grouping dimensions. Date ranges are half-open (`from <= occurred_at < to`) so adjacent reports do not overlap.

Transactions use keyset pagination:
`ORDER BY occurred_at DESC, id DESC` and `(occurred_at,id) < (:cursorAt,:cursorId)`. There is no OFFSET.

Indexes:
- `(customer_id, occurred_at DESC, id DESC)` supports the dominant report date scan and transaction pagination.
- `(customer_id, category, occurred_at DESC, id DESC)` supports category-filtered reports.
- `(customer_id, team_id)` supports team-oriented filtering/join access.

The seed creates 50 customers and 10 teams per customer and intentionally puts 80% of transactions on customer 1.

The cache is in-process with a 30-second TTL. The key contains customer, time range, grouping and category. A per-key `ReentrantLock` provides single-flight behavior: concurrent misses for the same key perform one database query. Writes invalidate all report entries belonging only to the affected customer.

## Trade-offs

Indexes improve reads but increase INSERT cost and storage. The composite indexes were chosen around the report and keyset access patterns rather than indexing every column.

The cache favors latency while keeping correctness through customer-scoped invalidation after a committed insert. If cache state is lost, requests simply query PostgreSQL again. If PostgreSQL is slow, requests wait on the database; cache hits remain available until TTL expiry.

For several service instances, an in-process cache would no longer be globally coherent. I would use a distributed cache plus versioned/customer-scoped invalidation, or publish invalidation events. At 100x the data, I would consider read replicas for report traffic and a pre-aggregated monthly/customer/team/category table maintained transactionally or asynchronously, depending on freshness requirements.

## Measurements

Run the simple query and optimized query plans against the seeded database and save them under `docs/explain/`. The repository is structured so reviewer-generated plans remain reproducible on the same hardware.

Example:

```bash
docker compose up --build
docker compose exec postgres psql -U spend -d spend
```

Then run the SQL in `docs/explain/before.sql` and `docs/explain/after.sql` with `EXPLAIN (ANALYZE, BUFFERS)` and redirect output to the corresponding `.txt` files.

Run the load test:

```bash
chmod +x load-test.sh
./load-test.sh
```

Record separate runs for customer 1 (large) and customer 50 (small) before and after indexing/cache warmup in `docs/load-results.md`.

## AI usage

AI assistance was used to accelerate boilerplate generation and review of the implementation. Database access patterns, index choices, cache consistency rules, and correctness constraints should be validated by running the supplied tests and PostgreSQL execution plans locally.

## How to run

Requirements: Docker and Docker Compose.

```bash
docker compose up --build
```

The application waits for PostgreSQL, Flyway applies migrations, and the seed command generates at least 2 million transactions.

API examples:

```bash
curl 'http://localhost:8080/customers/1/reports/spend?from=2025-01-01T00:00:00Z&to=2027-01-01T00:00:00Z&group_by=team,category,month'
```

```bash
curl 'http://localhost:8080/customers/1/transactions?limit=100'
```

```bash
curl -X POST 'http://localhost:8080/customers/1/transactions' \
  -H 'Content-Type: application/json' \
  -d '{"teamId":1,"category":"software","amount":1299,"currency":"EUR","occurredAt":"2026-10-28T10:00:00Z"}'
```

The implementation deliberately keeps the API small and has no authentication or UI, as required by the task.
