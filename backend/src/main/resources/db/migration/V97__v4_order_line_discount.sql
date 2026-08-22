-- V97: Patch v4.0.0 order line discount column
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS line_discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
