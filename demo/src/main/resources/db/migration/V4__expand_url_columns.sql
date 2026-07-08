-- V4__expand_url_columns.sql
-- Expand URL column lengths to allow long image links (up to 4000 characters)

ALTER TABLE dishes ALTER COLUMN image_url VARCHAR(4000);
ALTER TABLE users ALTER COLUMN avatar_url VARCHAR(4000);
