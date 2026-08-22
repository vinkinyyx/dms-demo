DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'product_prices_tenant_id_product_id_partner_type_partner_id_key'
    ) THEN
        ALTER TABLE product_prices
            DROP CONSTRAINT product_prices_tenant_id_product_id_partner_type_partner_id_key;
    ELSIF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'product_prices_tenant_id_product_id_partner_type_partner_id_key'
    ) THEN
        DROP INDEX product_prices_tenant_id_product_id_partner_type_partner_id_key;
    END IF;
END $$;
