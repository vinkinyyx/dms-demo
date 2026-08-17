-- V85: Backfill missing detail lines for demo/historical orders and stock moves.
-- Several demo sales/purchase orders and stock moves were created without child lines,
-- which made order/move detail pages show an empty product table (BUG-015/017).
-- For any line-less order/move, attach one active product from the same tenant so the
-- detail view is no longer empty. Newly created documents already persist lines correctly.

DO $$
DECLARE tid UUID := '11111111-1111-1111-1111-111111111111';
BEGIN
  -- Sales/purchase orders without lines
  INSERT INTO order_lines (order_id, seq, product_id, qty, unit_price, tax_rate, sub_total, is_gift, created_at, updated_at)
  SELECT o.id, 1,
         COALESCE((SELECT p.id FROM products p WHERE p.tenant_id = o.tenant_id AND p.status = 'active' ORDER BY p.id LIMIT 1),
                  (SELECT p.id FROM products p WHERE p.tenant_id = o.tenant_id ORDER BY p.id LIMIT 1)),
         1, 0, 0.13, 0, false, now(), now()
  FROM orders o
  WHERE o.tenant_id = tid
    AND o.deleted_at IS NULL
    AND NOT EXISTS (SELECT 1 FROM order_lines ol WHERE ol.order_id = o.id);

  -- Stock moves without lines
  INSERT INTO stock_move_lines (move_id, product_id, qty, created_at)
  SELECT m.id,
         COALESCE((SELECT p.id FROM products p WHERE p.tenant_id = m.tenant_id AND p.status = 'active' ORDER BY p.id LIMIT 1),
                  (SELECT p.id FROM products p WHERE p.tenant_id = m.tenant_id ORDER BY p.id LIMIT 1)),
         1, now()
  FROM stock_moves m
  WHERE m.tenant_id = tid
    AND NOT EXISTS (SELECT 1 FROM stock_move_lines sml WHERE sml.move_id = m.id);
END $$;
