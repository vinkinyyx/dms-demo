-- V104: Recalculate orders whose PERCENT header discount was wrongly stored as a flat amount
-- by the pre-bugfix logic (value treated as currency). Repartitions the header discount
-- across lines proportional to sub_total; remainder assigned to the last line.

-- 1) header discount share per line
WITH ord AS (
  SELECT o.id AS oid, o.header_discount_value AS hv,
         SUM(ol.sub_total) AS std,
         LEAST(GREATEST(ROUND(SUM(ol.sub_total) * (o.header_discount_value/100.0), 2), 0), SUM(ol.sub_total)) AS hdr
  FROM orders o JOIN order_lines ol ON ol.order_id = o.id
  WHERE o.header_discount_type = 'PERCENT'
    AND o.header_discount_value IS NOT NULL
    AND o.deleted_at IS NULL
  GROUP BY o.id, o.header_discount_value
),
ranked AS (
  SELECT ol.id, ol.order_id, ol.sub_total,
         ROW_NUMBER() OVER (PARTITION BY ol.order_id ORDER BY ol.seq, ol.id) AS rn,
         COUNT(*) OVER (PARTITION BY ol.order_id) AS cnt,
         ord.hdr, ord.std,
         ROUND(ord.hdr * ol.sub_total / NULLIF(ord.std,0), 2) AS share
  FROM order_lines ol JOIN ord ON ord.oid = ol.order_id
),
togo AS (
  SELECT id, order_id, sub_total, hdr, std, share, rn, cnt,
         COALESCE(SUM(share) OVER (PARTITION BY order_id ORDER BY rn ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0) AS before_sum
  FROM ranked
)
UPDATE order_lines ol
SET header_discount_amount = CASE WHEN t.rn = t.cnt THEN ROUND(t.hdr - t.before_sum, 2) ELSE t.share END,
    discount_amount = ol.line_discount_amount + ol.promo_discount_amount + (CASE WHEN t.rn = t.cnt THEN ROUND(t.hdr - t.before_sum, 2) ELSE t.share END),
    final_amount = GREATEST(ol.sub_total - ol.line_discount_amount - ol.promo_discount_amount - (CASE WHEN t.rn = t.cnt THEN ROUND(t.hdr - t.before_sum, 2) ELSE t.share END), 0),
    amount_excl_tax = ROUND(GREATEST(ol.sub_total - ol.line_discount_amount - ol.promo_discount_amount - (CASE WHEN t.rn = t.cnt THEN ROUND(t.hdr - t.before_sum, 2) ELSE t.share END), 0) / (1 + COALESCE(ol.tax_rate,0)), 2),
    updated_at = now()
FROM togo t
WHERE ol.id = t.id;

-- 2) order totals
WITH ord AS (
  SELECT o.id AS oid,
         SUM(ol.sub_total) AS std,
         LEAST(GREATEST(ROUND(SUM(ol.sub_total) * (o.header_discount_value/100.0), 2), 0), SUM(ol.sub_total)) AS hdr
  FROM orders o JOIN order_lines ol ON ol.order_id = o.id
  WHERE o.header_discount_type = 'PERCENT'
    AND o.header_discount_value IS NOT NULL
    AND o.deleted_at IS NULL
  GROUP BY o.id, o.header_discount_value
)
UPDATE orders o
SET discount_amount = ord.hdr,
    final_amount = ord.std - ord.hdr,
    amount_excl_tax = ROUND((ord.std - ord.hdr)/(1+0.13),2),
    tax_amount = (ord.std - ord.hdr) - ROUND((ord.std - ord.hdr)/(1+0.13),2),
    updated_at = now()
FROM ord
WHERE o.id = ord.oid;

