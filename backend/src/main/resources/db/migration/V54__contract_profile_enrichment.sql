-- Contract profile enrichment: party, term, amount, targets and settlement metadata.
ALTER TABLE contracts
  ADD COLUMN IF NOT EXISTS contract_name VARCHAR(160),
  ADD COLUMN IF NOT EXISTS vendor_party VARCHAR(160),
  ADD COLUMN IF NOT EXISTS dealer_party VARCHAR(160),
  ADD COLUMN IF NOT EXISTS sign_city VARCHAR(80),
  ADD COLUMN IF NOT EXISTS contract_type VARCHAR(32),
  ADD COLUMN IF NOT EXISTS business_scope TEXT,
  ADD COLUMN IF NOT EXISTS target_amount NUMERIC(14,2),
  ADD COLUMN IF NOT EXISTS signed_amount NUMERIC(14,2),
  ADD COLUMN IF NOT EXISTS rebate_rate NUMERIC(8,4),
  ADD COLUMN IF NOT EXISTS payment_terms VARCHAR(160),
  ADD COLUMN IF NOT EXISTS settlement_cycle VARCHAR(64),
  ADD COLUMN IF NOT EXISTS delivery_terms VARCHAR(160),
  ADD COLUMN IF NOT EXISTS owner_name VARCHAR(64),
  ADD COLUMN IF NOT EXISTS owner_phone VARCHAR(32),
  ADD COLUMN IF NOT EXISTS renew_before_days INTEGER DEFAULT 30,
  ADD COLUMN IF NOT EXISTS terminated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_contracts_profile_dealer_valid
  ON contracts(tenant_id, dealer_id, valid_from, valid_to);

UPDATE contracts
SET contract_name = COALESCE(NULLIF(contract_name, ''), code || '-' || CASE category
    WHEN 'DISTRIBUTION' THEN '经销协议'
    WHEN 'SERVICE' THEN '服务协议'
    WHEN 'PROMOTION' THEN '推广协议'
    WHEN 'AUTHORIZATION' THEN '授权协议'
    ELSE '商业合同' END || '-' || to_char(valid_from, 'YYYY'))
WHERE contract_name IS NULL OR contract_name = '';

UPDATE contracts
SET vendor_party = COALESCE(NULLIF(contracts.vendor_party, ''), '上海鼎医智能科技有限公司'),
    dealer_party = COALESCE(dealer_party, d.name),
    contract_type = COALESCE(NULLIF(contracts.contract_type, ''), CASE WHEN contracts.category = 'AUTHORIZATION' THEN 'AUTH' ELSE 'FRAMEWORK' END),
    business_scope = COALESCE(NULLIF(contracts.business_scope, ''), '医疗器械产品销售、配送、市场推广及售后服务；按订单与附件执行。'),
    target_amount = COALESCE(contracts.target_amount, CASE
       WHEN contracts.category = 'DISTRIBUTION' THEN 1200000 + (contracts.id % 9) * 180000
       WHEN contracts.category = 'SERVICE' THEN 360000 + (contracts.id % 5) * 90000
       WHEN contracts.category = 'PROMOTION' THEN 280000 + (contracts.id % 4) * 80000
       ELSE 600000 + (contracts.id % 6) * 120000 END),
    signed_amount = COALESCE(contracts.signed_amount, contracts.target_amount * (0.82 + (contracts.id % 18)::numeric / 100)),
    rebate_rate = COALESCE(contracts.rebate_rate, CASE
       WHEN contracts.category = 'DISTRIBUTION' THEN 0.045 + (contracts.id % 4) * 0.005
       WHEN contracts.category = 'PROMOTION' THEN 0.03 + (contracts.id % 3) * 0.005
       ELSE 0 END),
    payment_terms = COALESCE(NULLIF(contracts.payment_terms, ''), '月结60天，验收合格后凭发票付款'),
    settlement_cycle = COALESCE(NULLIF(contracts.settlement_cycle, ''), CASE WHEN contracts.category = 'PROMOTION' THEN '季度结算' ELSE '月度对账' END),
    delivery_terms = COALESCE(NULLIF(contracts.delivery_terms, ''), '订单确认后5个工作日内发货，送达经销商指定仓库'),
    owner_name = COALESCE(NULLIF(contracts.owner_name, ''), '区域经理-' || LPAD(((contracts.id % 12) + 1)::text, 2, '0')),
    owner_phone = COALESCE(NULLIF(contracts.owner_phone, ''), '138' || LPAD(((contracts.id % 10000)::text), 4, '0')),
    renew_before_days = COALESCE(contracts.renew_before_days, 30)
FROM dealers d
WHERE d.id = contracts.dealer_id;
