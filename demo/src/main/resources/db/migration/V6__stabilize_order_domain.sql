-- V6__stabilize_order_domain.sql
-- Stabilize core order domain fields for enum strings, VND money, snapshots, and optimistic locking.

ALTER TABLE dishes ALTER COLUMN price NUMERIC(19, 0);
ALTER TABLE order_items ALTER COLUMN price NUMERIC(19, 0);
ALTER TABLE orders ALTER COLUMN total_amount NUMERIC(19, 0);

ALTER TABLE orders ADD COLUMN subtotal NUMERIC(19, 0) DEFAULT 0 NOT NULL;
ALTER TABLE orders ADD COLUMN discount_amount NUMERIC(19, 0) DEFAULT 0 NOT NULL;
ALTER TABLE orders ADD COLUMN service_charge NUMERIC(19, 0) DEFAULT 0 NOT NULL;
ALTER TABLE orders ADD COLUMN delivery_fee NUMERIC(19, 0) DEFAULT 0 NOT NULL;
ALTER TABLE orders ADD COLUMN tax_amount NUMERIC(19, 0) DEFAULT 0 NOT NULL;
ALTER TABLE orders ADD COLUMN order_type VARCHAR(30) DEFAULT 'DINE_IN' NOT NULL;
ALTER TABLE orders ADD COLUMN paid_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN paid_by BIGINT REFERENCES users(id);
ALTER TABLE orders ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

UPDATE orders SET subtotal = COALESCE(total_amount, 0), total_amount = COALESCE(total_amount, 0);
UPDATE orders SET status = 'CONFIRMED' WHERE status = 'PENDING' AND id IN (SELECT order_id FROM order_items WHERE status IN ('PREPARING', 'READY', 'SERVED'));
UPDATE orders SET status = 'READY' WHERE status NOT IN ('COMPLETED', 'CANCELLED') AND id IN (SELECT order_id FROM order_items WHERE status = 'READY');

ALTER TABLE order_items ADD COLUMN dish_name_snapshot VARCHAR(255);
ALTER TABLE order_items ADD COLUMN line_total NUMERIC(19, 0) DEFAULT 0 NOT NULL;
ALTER TABLE order_items ADD COLUMN note VARCHAR(255);
ALTER TABLE order_items ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

UPDATE order_items
SET dish_name_snapshot = (SELECT d.name FROM dishes d WHERE d.id = order_items.dish_id)
WHERE dish_name_snapshot IS NULL;
UPDATE order_items SET line_total = COALESCE(price, 0) * COALESCE(quantity, 0);
UPDATE order_items SET status = 'PREPARING' WHERE status = 'COOKING';

ALTER TABLE order_items ALTER COLUMN dish_name_snapshot SET NOT NULL;

ALTER TABLE restaurant_tables ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE dishes ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
