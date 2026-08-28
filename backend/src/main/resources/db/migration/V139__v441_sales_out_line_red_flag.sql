ALTER TABLE sales_out_lines ADD COLUMN IF NOT EXISTS is_red boolean NOT NULL DEFAULT false;

UPDATE sales_out_lines sol
SET is_red = COALESCE(so.is_red, false)
FROM sales_outs so
WHERE sol.sales_out_id = so.id
  AND COALESCE(sol.is_red, false) <> COALESCE(so.is_red, false);

DROP INDEX IF EXISTS ux_sales_serial;

CREATE UNIQUE INDEX ux_sales_serial
ON sales_out_lines(serial_no)
WHERE serial_no IS NOT NULL AND COALESCE(is_red, false) = false;
