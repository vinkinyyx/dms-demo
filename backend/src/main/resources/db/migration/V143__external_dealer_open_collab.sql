-- =====================================================================
-- V143: 厂家 DMS <-> 平台外下游经销商 报文式协同开放接口
--   1. open_app 扩展：外部经销商绑定 + webhook 回调地址（发货/红字发货回传）
--   2. open_partner_materials：外部物料编码 -> 厂家产品 映射（按经销商维度）
--   3. open_collab_messages：报文台账（入站幂等 + 出站推送状态/重试）
--   4. sales_outs 补充物流公司/运单号（出库回传报文表头字段）
--   5. 演示种子：外部经销商 EXT-D1 + 开放应用 dms-ext-dealer-d1 + 物料映射
-- 鉴权复用 OpenApiAuthFilter（/open/api/**，HMAC-SHA256，X-App-Key/X-Signature）
-- =====================================================================

-- 1. open_app 扩展列 ---------------------------------------------------
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS partner_type   VARCHAR(16)  DEFAULT 'ERP';
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS dealer_code   VARCHAR(32);
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS dealer_id     BIGINT;
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS webhook_url   VARCHAR(512);
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS webhook_secret VARCHAR(128);
COMMENT ON COLUMN open_app.partner_type IS '对接方类型：ERP=厂家ERP；DEALER=平台外下游经销商';
COMMENT ON COLUMN open_app.dealer_code IS '外部经销商在厂家租户内的客户主数据编码（dealers.code），报文 dealerCode 以此为准';
COMMENT ON COLUMN open_app.webhook_url IS '出库/红字出库发货回传报文推送地址（经销商自有系统接收）';
COMMENT ON COLUMN open_app.webhook_secret IS '出站 webhook 签名密钥，缺省用 app_secret';

-- 2. 外部物料编码映射 ---------------------------------------------------
CREATE TABLE IF NOT EXISTS open_partner_materials (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    app_id          BIGINT NOT NULL,
    dealer_code     VARCHAR(32) NOT NULL,
    external_code   VARCHAR(64) NOT NULL,
    external_name   VARCHAR(200),
    product_id      BIGINT NOT NULL,
    product_code    VARCHAR(64) NOT NULL,
    status          VARCHAR(16) DEFAULT 'active',
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    version         INT DEFAULT 0,
    deleted_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_opm_ext_code ON open_partner_materials(app_id, external_code) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_opm_product ON open_partner_materials(tenant_id, product_id);
CREATE INDEX IF NOT EXISTS idx_opm_dealer ON open_partner_materials(tenant_id, dealer_code);

-- 3. 报文台账（入站幂等 + 出站推送/重试） --------------------------------
CREATE TABLE IF NOT EXISTS open_collab_messages (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    app_id            BIGINT,
    app_key           VARCHAR(64),
    direction         VARCHAR(8)  NOT NULL,   -- IN / OUT
    msg_type          VARCHAR(32) NOT NULL,   -- PURCHASE_ORDER / SHIP_NOTICE / PURCHASE_RETURN / RED_SHIP_NOTICE
    partner_doc_no    VARCHAR(64),            -- 入站：经销商单号(poNo/returnNo)；出站：厂家出库单号
    local_doc_no      VARCHAR(64),            -- 入站：厂家销售订单号；出站：同 partner_doc_no（出库单号）
    dealer_code       VARCHAR(32),
    webhook_url       VARCHAR(512),
    line_refs         JSONB,              -- 出站：本次推送的出库执行行ID集合（分批发货幂等）
    request_body      TEXT,
    response_body     TEXT,
    status            VARCHAR(16) NOT NULL DEFAULT 'NEW', -- IN: PROCESSED/IDEMPOTENT/FAILED ; OUT: PENDING/SUCCESS/FAILED
    http_status       INT,
    error_msg         TEXT,
    retry_count       INT DEFAULT 0,
    next_retry_at     TIMESTAMPTZ,
    last_sent_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now()
);
-- 入站幂等：同一应用同一报文类型同一经销商单号只处理一次
CREATE UNIQUE INDEX IF NOT EXISTS ux_ocm_in_idem
    ON open_collab_messages(app_id, msg_type, partner_doc_no)
    WHERE direction = 'IN';
-- 出站幂等：同一张出库单（分批发货每条 out_line 集合不同，用 partner_doc_no+line_refs 区分）
CREATE INDEX IF NOT EXISTS idx_ocm_out ON open_collab_messages(direction, msg_type, status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_ocm_tenant ON open_collab_messages(tenant_id, created_at DESC);

-- 4. sales_outs 物流字段（出库回传报文表头） ------------------------------
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS logistics_company VARCHAR(128);
ALTER TABLE sales_outs ADD COLUMN IF NOT EXISTS tracking_no       VARCHAR(64);

-- 5. 演示种子：外部经销商 + 开放应用 + 物料映射 ---------------------------
-- 5.1 厂家(default)租户内的外部经销商客户主数据
INSERT INTO dealers (tenant_id, code, name, level, status, created_at, updated_at, version)
SELECT t.id, 'EXT-D1', '外部演示经销商（报文对接）', 'external', 'active', now(), now(), 0
FROM tenants t
WHERE t.code = 'default'
  AND NOT EXISTS (SELECT 1 FROM dealers d WHERE d.tenant_id = t.id AND d.code = 'EXT-D1' AND d.deleted_at IS NULL);

-- 5.2 开放应用（AppKey/AppSecret 机器凭证，HMAC 签名；webhook 指向占位地址，联调时更新）
INSERT INTO open_app (tenant_id, app_key, app_secret, app_name, system, partner_type,
                      dealer_code, dealer_id, webhook_url, webhook_secret, status, created_at, updated_at)
SELECT t.id, 'dms-ext-dealer-d1', 'ext-dealer-d1-secret-20260901', '外部演示经销商D1', 'DEALER_ERP', 'DEALER',
       'EXT-D1', d.id, 'http://127.0.0.1:9999/webhook/collab', 'ext-dealer-d1-whsec-20260901', 'active', now(), now()
FROM tenants t
JOIN dealers d ON d.tenant_id = t.id AND d.code = 'EXT-D1' AND d.deleted_at IS NULL
WHERE t.code = 'default'
  AND NOT EXISTS (SELECT 1 FROM open_app a WHERE a.app_key = 'dms-ext-dealer-d1');

-- 5.3 外部物料编码映射：外部编码 EXT-MAT-001..003 -> 厂家产品 PROD-000001..003（V7 演示产品）
INSERT INTO open_partner_materials (tenant_id, app_id, dealer_code, external_code, external_name,
                                    product_id, product_code, status, created_at, updated_at)
SELECT t.id, a.id, 'EXT-D1', v.ext_code, v.ext_name, p.id, p.code, 'active', now(), now()
FROM tenants t
JOIN open_app a ON a.tenant_id = t.id AND a.app_key = 'dms-ext-dealer-d1'
CROSS JOIN (VALUES
    ('EXT-MAT-001', '外部物料-支架001', 'PROD-000001'),
    ('EXT-MAT-002', '外部物料-支架002', 'PROD-000002'),
    ('EXT-MAT-003', '外部物料-器械003', 'PROD-000003')
) AS v(ext_code, ext_name, mfr_code)
JOIN products p ON p.tenant_id = t.id AND p.code = v.mfr_code AND p.deleted_at IS NULL
WHERE t.code = 'default'
  AND NOT EXISTS (SELECT 1 FROM open_partner_materials m
                  WHERE m.app_id = a.id AND m.external_code = v.ext_code AND m.deleted_at IS NULL);
