DO $$
DECLARE tid uuid := '11111111-1111-1111-1111-111111111111';
BEGIN
  INSERT INTO rebate_previews (
    tenant_id, dealer_id, period_yyyymm, target_amount, actual_amount, achievement_rate, tier_hit,
    gross_rebate, deductions, net_rebate, snapshot_at, created_at, updated_at, version
  )
  SELECT tid, d.id, to_char(gs, 'YYYYMM'),
    ROUND(base.target_amount::numeric, 2),
    ROUND((base.target_amount * base.ach_factor)::numeric, 2),
    0, jsonb_build_object('tier', 'T1'), 0, '{}'::jsonb, 0, now(), now(), now(), 0
  FROM dealers d
  CROSS JOIN LATERAL (
    SELECT gs,
      (CASE d.level WHEN 'T1' THEN 880000 WHEN 'T2' THEN 500000 ELSE 260000 END
        + d.id * 15500 + EXTRACT(MONTH FROM gs) * 17000) AS target_amount,
      0.74 + 0.032 * sin((EXTRACT(MONTH FROM gs) + d.id)::float / 2.0)
        + CASE WHEN d.level = 'T1' THEN 0.19 WHEN d.level = 'T2' THEN 0.11 ELSE 0.05 END AS ach_factor
    FROM generate_series(DATE '2025-01-01', DATE '2026-12-01', INTERVAL '1 month') gs
  ) base
  WHERE d.tenant_id = tid
    AND NOT EXISTS (
      SELECT 1 FROM rebate_previews rp
      WHERE rp.tenant_id = tid AND rp.dealer_id = d.id AND rp.period_yyyymm = to_char(base.gs, 'YYYYMM')
    );

  WITH normalized AS (
    SELECT rp.id,
      ROUND(rp.actual_amount / NULLIF(rp.target_amount, 0), 4) AS achievement_rate,
      CASE
        WHEN rp.actual_amount >= rp.target_amount * 1.10 THEN 0.058
        WHEN rp.actual_amount >= rp.target_amount * 0.95 THEN 0.045
        WHEN rp.actual_amount >= rp.target_amount * 0.80 THEN 0.032
        ELSE 0.018
      END AS rebate_rate,
      CASE
        WHEN rp.actual_amount >= rp.target_amount * 1.10 THEN 'T3'
        WHEN rp.actual_amount >= rp.target_amount * 0.95 THEN 'T2'
        ELSE 'T1'
      END AS tier_hit,
      CASE (rp.id % 5)
        WHEN 0 THEN 0.08 WHEN 1 THEN 0.05 WHEN 2 THEN 0.03 WHEN 3 THEN 0.02 ELSE 0.04
      END AS deduction_rate
    FROM rebate_previews rp
    WHERE rp.tenant_id = tid AND rp.gross_rebate = 0
  )
  UPDATE rebate_previews rp SET
    achievement_rate = n.achievement_rate,
    tier_hit = jsonb_build_object('tier', n.tier_hit),
    gross_rebate = ROUND(rp.actual_amount * n.rebate_rate, 2),
    deductions = jsonb_build_object('reason', CASE (rp.id % 5) WHEN 0 THEN '返利扣减' WHEN 1 THEN '价保扣减' WHEN 2 THEN '窜货扣减' WHEN 3 THEN '促销补差' ELSE '考核扣减' END, 'amount', ROUND(rp.actual_amount * n.rebate_rate * n.deduction_rate, 2)),
    net_rebate = ROUND(rp.actual_amount * n.rebate_rate * (1 - n.deduction_rate), 2),
    updated_at = now()
  FROM normalized n
  WHERE rp.id = n.id;

  WITH zero_dealers AS (
    SELECT d.id AS dealer_id
    FROM dealers d
    WHERE d.tenant_id = tid
      AND NOT EXISTS (SELECT 1 FROM inventory i WHERE i.tenant_id = tid AND i.dealer_id = d.id)
  ), source_inventory AS (
    SELECT p.id AS product_id,
           COALESCE((SELECT w.id FROM warehouses w WHERE w.tenant_id = tid ORDER BY w.id LIMIT 1), 1) AS warehouse_id
    FROM products p
    WHERE p.tenant_id = tid AND p.status = 'active'
  ), batches AS (
    SELECT z.dealer_id, s.product_id, s.warehouse_id,
      'B' || to_char(DATE '2025-01-10' + ((s.product_id * 37 + z.dealer_id * 11) % 540) * INTERVAL '1 day', 'YYYYMMDD') || '-' || lpad(((s.product_id + z.dealer_id) % 90 + 1)::text, 3, '0') AS batch_no,
      DATE '2025-01-10' + ((s.product_id * 37 + z.dealer_id * 11) % 540) * INTERVAL '1 day' AS prod_date,
      DATE '2027-01-10' + ((s.product_id * 37 + z.dealer_id * 11) % 540) * INTERVAL '1 day' AS exp_date,
      GREATEST(6, (s.product_id + z.dealer_id) % 80 + 4) AS qty,
      CASE WHEN (s.product_id + z.dealer_id) % 17 = 0 THEN 'PENDING'
           WHEN (s.product_id + z.dealer_id) % 29 = 0 THEN 'DEFECTIVE'
           ELSE 'QUALIFIED' END AS stock_status
    FROM zero_dealers z CROSS JOIN source_inventory s
  )
  INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, batch_no, prod_date, exp_date, qty, in_source, created_at, updated_at, version, stock_status)
  SELECT tid, dealer_id, warehouse_id, product_id, batch_no, prod_date, exp_date, qty, 'MIGRATION', now(), now(), 0, stock_status
  FROM batches
  ON CONFLICT (tenant_id, warehouse_id, product_id, batch_no, serial_no) DO NOTHING;
END $$;
