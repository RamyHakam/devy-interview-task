-- Baseline report query BEFORE indexes.
-- Run:
-- EXPLAIN (ANALYZE, BUFFERS)
SELECT tm.name AS team, t.category AS category,
       date_trunc('month', t.occurred_at) AS month,
       SUM(t.amount) AS total_amount, COUNT(*) AS transaction_count
FROM transactions t
JOIN teams tm ON tm.id=t.team_id AND tm.customer_id=t.customer_id
WHERE t.customer_id=1
  AND t.occurred_at >= '2025-01-01T00:00:00Z'
  AND t.occurred_at < '2027-01-01T00:00:00Z'
GROUP BY tm.name,t.category,date_trunc('month',t.occurred_at);

-- Execute after V2__indexes.sql.
