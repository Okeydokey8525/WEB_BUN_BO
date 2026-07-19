-- V5__order_public_token.sql
-- Add public tracking token column for customer order status pages.
-- Existing rows may be backfilled by an application task or database-specific SQL before enforcing NOT NULL in production.

ALTER TABLE orders ADD COLUMN public_token VARCHAR(64);

CREATE UNIQUE INDEX ux_orders_public_token ON orders(public_token);
CREATE INDEX ix_orders_branch_status_created ON orders(branch_id, status, created_at);
CREATE INDEX ix_order_items_order_status ON order_items(order_id, status);
