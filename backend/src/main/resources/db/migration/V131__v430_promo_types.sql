-- v4.3.0 R4：扩展促销类型白名单，新增 QTY_DISCOUNT（满N件打折/固定单价）、QTY_REDUCE（满N件减金额）。
ALTER TABLE promotions DROP CONSTRAINT IF EXISTS ck_promo_type_v1;
ALTER TABLE promotions ADD CONSTRAINT ck_promo_type_v430
    CHECK (promo_type IN ('MOQ','FULL_REDUCTION','GIFT','BUNDLE','QTY_DISCOUNT','QTY_REDUCE'));
