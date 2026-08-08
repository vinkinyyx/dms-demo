-- V56: 补充 2026 年 8 月真实测试订单数据（约 60 张销售订单，覆盖 8 月 1-4 日）

-- ============================================================
-- 1. 生成销售订单
-- ============================================================
WITH dealer_pool AS (
  SELECT id, row_number() OVER (ORDER BY id) AS rn FROM dealers
  WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND deleted_at IS NULL
),
seed AS (
  SELECT
    gs AS ord_idx,
    (gs % 30) + 1 AS dealer_rn,
    timestamp '2026-08-01 08:00:00' + (gs * interval '42 min') + (random() * interval '20 min') AS base_time
  FROM generate_series(1, 60) AS gs
),
order_data AS (
  SELECT
    s.ord_idx,
    d.id AS dealer_id,
    s.base_time,
    CASE WHEN random() < 0.08 THEN 'URGENT' ELSE 'NORMAL' END AS order_type,
    CASE
      WHEN s.base_time < timestamp '2026-08-02' THEN (array['COMPLETED','COMPLETED','SHIPPING','APPROVED'])[1 + floor(random()*4)::int]
      WHEN s.base_time < timestamp '2026-08-03' THEN (array['COMPLETED','SHIPPING','APPROVED','APPROVED'])[1 + floor(random()*4)::int]
      WHEN s.base_time < timestamp '2026-08-04' THEN (array['SHIPPING','APPROVED','APPROVED','DRAFT'])[1 + floor(random()*4)::int]
      ELSE (array['APPROVED','DRAFT','DRAFT'])[1 + floor(random()*3)::int]
    END AS status
  FROM seed s
  JOIN dealer_pool d ON d.rn = s.dealer_rn
),
inserted AS (
  INSERT INTO orders (
    tenant_id, code, order_type, dealer_id, status,
    amount_incl_tax, discount_amount, final_amount, tax_amount,
    remark, expected_date, submitted_at, approved_at, shipped_at, received_at,
    completed_at, created_at, updated_at, version, is_red, extra
  )
  SELECT
    '11111111-1111-1111-1111-111111111111'::uuid,
    'SO-202608-DMO-' || lpad(ord_idx::text, 4, '0'),
    order_type, dealer_id, status,
    0, 0, 0, 0,
    CASE floor(random()*4)
      WHEN 0 THEN '常规补货订单'
      WHEN 1 THEN '高值耗材配送'
      WHEN 2 THEN '手术备货订单'
      ELSE '月度集中采购'
    END,
    (base_time + interval '3 days')::date,
    CASE WHEN status IN ('APPROVED','SHIPPING','COMPLETED') THEN base_time + interval '15 min' END,
    CASE WHEN status IN ('APPROVED','SHIPPING','COMPLETED') THEN base_time + interval '2 hours' END,
    CASE WHEN status IN ('SHIPPING','COMPLETED') THEN base_time + interval '1 day' END,
    CASE WHEN status = 'COMPLETED' THEN base_time + interval '3 days' END,
    CASE WHEN status = 'COMPLETED' THEN base_time + interval '3 days 4 hours' END,
    base_time, base_time, 0, false, '{}'::jsonb
  FROM order_data
  RETURNING id, created_at
)
SELECT count(*) AS orders_created FROM inserted;

-- ============================================================
-- 2. 为新订单生成订单行
-- ============================================================
WITH new_orders AS (
  SELECT id, created_at FROM orders
  WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
    AND code LIKE 'SO-202608-DMO-%'
),
product_pool AS (
  SELECT id, current_price, tax_rate,
    row_number() OVER (ORDER BY id) - 1 AS rn
  FROM products
  WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND deleted_at IS NULL
),
product_count AS (SELECT count(*) AS cnt FROM product_pool),
line_seed AS (
  SELECT
    o.id AS order_id,
    o.created_at,
    gs AS seq,
    floor(random() * (SELECT cnt FROM product_count))::bigint AS prod_idx,
    (1 + floor(random()*3))::numeric AS qty_multiplier
  FROM new_orders o
  CROSS JOIN generate_series(1, 1 + floor(random()*3)::int) AS gs
),
line_data AS (
  SELECT
    ls.order_id,
    p.id AS product_id,
    CASE
      WHEN p.current_price > 2000 THEN round((1 + random()*4)::numeric, 0)
      WHEN p.current_price > 500 THEN round((2 + random()*8)::numeric, 0)
      ELSE round((5 + random()*40)::numeric, 0)
    END AS qty,
    round(p.current_price::numeric, 2) AS unit_price,
    CASE
      WHEN p.current_price > 2000 THEN round((p.current_price * (1 + random()*4))::numeric, 2)
      WHEN p.current_price > 500 THEN round((p.current_price * (2 + random()*8))::numeric, 2)
      ELSE round((p.current_price * (5 + random()*40))::numeric, 2)
    END AS sub_total,
    COALESCE(p.tax_rate, 0.13) AS tax_rate,
    ls.seq,
    ls.created_at
  FROM line_seed ls
  JOIN product_pool p ON p.rn = ls.prod_idx
)
INSERT INTO order_lines (order_id, product_id, qty, unit_price, tax_rate, sub_total, is_gift, seq, created_at, updated_at, extra)
SELECT order_id, product_id, qty, unit_price, tax_rate, sub_total, false, seq, created_at, created_at, '{}'::jsonb
FROM line_data;

-- ============================================================
-- 3. 回写订单金额
-- ============================================================
UPDATE orders o SET
  amount_incl_tax = sub.total_amount,
  final_amount = sub.total_amount,
  tax_amount = round(sub.total_amount::numeric * 0.13, 2),
  updated_at = now()
FROM (
  SELECT order_id, SUM(sub_total) AS total_amount
  FROM order_lines
  WHERE order_id IN (SELECT id FROM orders WHERE code LIKE 'SO-202608-DMO-%')
  GROUP BY order_id
) sub
WHERE o.id = sub.order_id;

SELECT setval('orders_id_seq', (SELECT max(id) FROM orders));
SELECT setval('order_lines_id_seq', (SELECT max(id) FROM order_lines));