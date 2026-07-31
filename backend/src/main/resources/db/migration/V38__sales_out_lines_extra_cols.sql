-- V38: sales_out_lines 继续补齐实体引用但旧表缺失的列
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS stock_batch_id BIGINT;
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS subtotal       NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS tax_rate       NUMERIC(5,4)  DEFAULT 0.13;
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS unit_price     NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS seq            INT DEFAULT 1;