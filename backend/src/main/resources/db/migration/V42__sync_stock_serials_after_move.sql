-- V42: repair stock_serials after inventory move/status adjustment.
-- Inventory moves update the inventory table, but previously did not sync stock_serials.

ALTER TABLE stock_serials
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

UPDATE stock_serials ss
SET warehouse_id = sub.warehouse_id,
    stock_status = sub.stock_status,
    shipped_at = CASE WHEN sub.qty > 0 THEN NULL ELSE ss.shipped_at END,
    updated_at = now()
FROM (
    SELECT DISTINCT ON (i.tenant_id, i.product_id, COALESCE(i.batch_no, ''), i.serial_no)
           i.tenant_id, i.warehouse_id, i.product_id, i.batch_no, i.serial_no,
           i.stock_status, i.qty
    FROM inventory i
    WHERE i.serial_no IS NOT NULL
      AND i.qty > 0
    ORDER BY i.tenant_id, i.product_id, COALESCE(i.batch_no, ''), i.serial_no, i.updated_at DESC NULLS LAST, i.id DESC
) sub
WHERE ss.tenant_id = sub.tenant_id
  AND ss.batch_no IS NOT DISTINCT FROM sub.batch_no
  AND ss.serial_no = sub.serial_no
  AND ss.product_id = sub.product_id
  AND (
      ss.warehouse_id <> sub.warehouse_id
      OR ss.product_id <> sub.product_id
      OR ss.stock_status <> sub.stock_status
      OR (sub.qty > 0 AND ss.shipped_at IS NOT NULL)
  );
