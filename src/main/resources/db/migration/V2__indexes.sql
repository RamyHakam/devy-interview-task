
CREATE INDEX idx_transactions_customer_occurred_id
    ON transactions (customer_id, occurred_at DESC, id DESC);

CREATE INDEX idx_transactions_customer_category_occurred
    ON transactions (customer_id, category, occurred_at DESC, id DESC);

CREATE INDEX idx_transactions_customer_team
    ON transactions (customer_id, team_id);
