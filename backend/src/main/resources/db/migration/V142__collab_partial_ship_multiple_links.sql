-- V142 v4.5.1 跨租户协同：同一张销售出库单分批发货时，每批发货回传一张经销商收货单，
-- cross_tenant_doc_links 对 sales_out_id 不再唯一（V140 的唯一索引 ux_ctdl_sales_out 会导致第二批回传 INSERT 失败）。
-- 幂等粒度改为"出库单 + 发货执行行（line_refs.outLineId）"，由应用层保证。
-- PO_TO_SALES_ORDER 的部分唯一索引 ux_ctdl_po 保留（路径A 一张采购单只对应一张销售订单）。
DROP INDEX IF EXISTS ux_ctdl_sales_out;
CREATE INDEX IF NOT EXISTS ix_ctdl_sales_out ON cross_tenant_doc_links(sales_out_id);
CREATE INDEX IF NOT EXISTS ix_ctdl_sales_order ON cross_tenant_doc_links(sales_order_id);
