-- v4.0.0-bugfix.x: 修正测试数据中小数数量，发货/退货数量必须为整数
UPDATE sales_out_lines SET qty = ROUND(qty) WHERE qty IS NOT NULL AND qty <> ROUND(qty);
UPDATE sales_out_lines SET shipped_qty = ROUND(shipped_qty) WHERE shipped_qty IS NOT NULL AND shipped_qty <> ROUND(shipped_qty);
UPDATE sales_out_lines SET expected_qty = ROUND(expected_qty) WHERE expected_qty IS NOT NULL AND expected_qty <> ROUND(expected_qty);
UPDATE order_lines SET qty = ROUND(qty) WHERE qty IS NOT NULL AND qty <> ROUND(qty);
