ALTER TABLE rma_order_lines ADD COLUMN IF NOT EXISTS serial_no VARCHAR(128);

UPDATE rma_order_lines rol
SET serial_no = sol.serial_no
FROM sales_out_lines sol
WHERE rol.sales_out_line_id = sol.id
  AND rol.serial_no IS NULL
  AND sol.serial_no IS NOT NULL
  AND rol.qty = 1;
