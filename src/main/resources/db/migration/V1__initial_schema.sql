CREATE TABLE customers (
 id BIGSERIAL PRIMARY KEY, name VARCHAR(160) NOT NULL, credit_limit NUMERIC(14,2) NOT NULL CHECK (credit_limit >= 0),
 credit_used NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (credit_used >= 0)
);
CREATE TABLE products (
 sku VARCHAR(80) PRIMARY KEY, unit_price NUMERIC(14,2) NOT NULL CHECK (unit_price >= 0),
 stock_quantity INTEGER NOT NULL CHECK (stock_quantity >= 0)
);
CREATE TABLE quotes (
 id UUID PRIMARY KEY, customer_id BIGINT NOT NULL REFERENCES customers(id),
 total NUMERIC(14,2) NOT NULL, expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE quote_items (
 id BIGSERIAL PRIMARY KEY, quote_id UUID NOT NULL REFERENCES quotes(id),
 sku VARCHAR(80) NOT NULL, quantity INTEGER NOT NULL CHECK (quantity > 0),
 unit_price NUMERIC(14,2) NOT NULL CHECK (unit_price >= 0), line_total NUMERIC(14,2) NOT NULL
);
CREATE INDEX idx_quote_items_quote ON quote_items(quote_id);
CREATE TABLE orders (
 id UUID PRIMARY KEY, quote_id UUID NOT NULL UNIQUE REFERENCES quotes(id),
 customer_id BIGINT NOT NULL REFERENCES customers(id), total NUMERIC(14,2) NOT NULL,
 status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_REVIEW','APPROVED','REJECTED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), reviewed_at TIMESTAMPTZ, reviewer VARCHAR(160), review_reason TEXT
);
CREATE TABLE order_items (
 id BIGSERIAL PRIMARY KEY, order_id UUID NOT NULL REFERENCES orders(id),
 sku VARCHAR(80) NOT NULL, quantity INTEGER NOT NULL, unit_price NUMERIC(14,2) NOT NULL, line_total NUMERIC(14,2) NOT NULL
);
CREATE TABLE idempotency_records (
 id BIGSERIAL PRIMARY KEY, idempotency_key VARCHAR(200) NOT NULL UNIQUE,
 request_hash VARCHAR(64) NOT NULL, order_id UUID REFERENCES orders(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE audit_records (
 id BIGSERIAL PRIMARY KEY, order_id UUID NOT NULL REFERENCES orders(id),
 actor VARCHAR(160) NOT NULL, action VARCHAR(80) NOT NULL, inputs TEXT NOT NULL,
 result TEXT NOT NULL, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_order ON audit_records(order_id, id);
-- Enforce append-only audit storage even if application code regresses.
CREATE FUNCTION prevent_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'audit_records are append-only'; END; $$;
CREATE TRIGGER audit_no_update BEFORE UPDATE OR DELETE ON audit_records
 FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

INSERT INTO customers(id,name,credit_limit,credit_used) VALUES
 (1,'Acme Industrial',25000.00,0),(2,'Northwind Materials',5000.00,0);
INSERT INTO products(sku,unit_price,stock_quantity) VALUES
 ('STEEL-PLATE-10',125.00,100),('COPPER-WIRE-5',89.50,40),('BOLT-M12',2.25,1000);
SELECT setval(pg_get_serial_sequence('customers','id'), (SELECT MAX(id) FROM customers));
