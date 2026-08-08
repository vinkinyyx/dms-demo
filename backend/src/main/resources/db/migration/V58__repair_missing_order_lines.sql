DO $$
DECLARE tid uuid := '11111111-1111-1111-1111-111111111111';
BEGIN
  INSERT INTO order_lines (order_id, product_id, qty, unit_price, tax_rate, sub_total, is_gift, seq, created_at, updated_at, extra)
  SELECT o.id,
    COALESCE((SELECT p.id FROM products p WHERE p.tenant_id = tid AND p.status = 'active' ORDER BY CASE WHEN o.id = 775 THEN 18 ELSE 1 END, p.id LIMIT 1), 1),
    CASE WHEN o.id = 775 THEN 4 ELSE 1 END,
    CASE WHEN o.id = 775 THEN 500.00 ELSE 4385.89 END,
    0.13,
    CASE WHEN o.id = 775 THEN 2000.00 ELSE 4385.89 END,
    false, 1, now(), now(), '{}'
  FROM orders o
  WHERE o.tenant_id = tid
    AND NOT EXISTS (SELECT 1 FROM order_lines ol WHERE ol.order_id = o.id)
    AND o.code IN ('SO-20260805-00002', 'TEST-DRAFT-2026-0724');

  UPDATE orders o SET
    amount_incl_tax = x.line_total,
    final_amount = x.line_total,
    tax_amount = ROUND(x.line_total - x.line_total / 1.13, 2),
    updated_at = now()
  FROM (
    SELECT ol.order_id, SUM(ol.sub_total) AS line_total
    FROM order_lines ol
    JOIN orders oo ON oo.id = ol.order_id
    WHERE oo.tenant_id = tid
    GROUP BY ol.order_id
  ) x
  WHERE o.id = x.order_id
    AND o.tenant_id = tid
    AND (o.amount_incl_tax IS DISTINCT FROM x.line_total OR o.final_amount IS DISTINCT FROM x.line_total);
END $$;
