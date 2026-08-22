-- v4.0.0-bugfix.2: 删除 BOM 历史遗留复合唯一索引，避免同编码多版本无法新建版本
DROP INDEX IF EXISTS uk_product_bundles_version;
DROP INDEX IF EXISTS product_bundles_tenant_id_product_id_code_bom_version_key;
