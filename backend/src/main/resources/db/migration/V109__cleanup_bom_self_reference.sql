-- v4.1.0: 清理 BOM 自引用（母件把自己作为子件）导致的 BOM_COMPONENT 脏数据。
-- 正常业务中 BOM 母件不应是自身的子件，计算器也会跳过自引用。
DELETE FROM product_bundle_lines
WHERE child_product_id IN (
  SELECT pb.product_id FROM product_bundles pb
  WHERE pb.id = product_bundle_lines.bundle_id
    AND pb.product_id = product_bundle_lines.child_product_id
    AND pb.deleted_at IS NULL
);

DELETE FROM product_prices
WHERE price_context = 'BOM_COMPONENT'
  AND bom_parent_product_id = product_id
  AND deleted_at IS NULL;