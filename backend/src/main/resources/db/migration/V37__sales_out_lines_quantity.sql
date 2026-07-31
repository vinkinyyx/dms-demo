-- V37: sales_out_lines 补齐 quantity 列（历史遗留字段，与 qty 同义）
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS quantity NUMERIC(14,4) DEFAULT 0;
UPDATE sales_out_lines SET quantity = COALESCE(qty, 0) WHERE quantity IS NULL OR quantity = 0;