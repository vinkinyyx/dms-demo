-- V76: 修复 V67 写入的乱码菜单/字典名称（V67 在部分环境因连接编码问题把中文写成了 '?'）。
-- 仅按 code/path 精确更新名称，不重建权限关系，避免影响已授权数据。

-- 1) 修复菜单资源名称（按 code + path 精确定位）
UPDATE resources SET name = '数据驾驶舱', updated_at = now()
WHERE code = 'dashboard:view' AND path = '/dashboard' AND name <> '数据驾驶舱';

UPDATE resources SET name = '产品管理', updated_at = now()
WHERE code = 'products:view' AND path = '/products' AND name <> '产品管理';

UPDATE resources SET name = '销售业绩排行', updated_at = now()
WHERE code = 'report_sales_ranking:view' AND path = '/reports?key=sales-ranking' AND name <> '销售业绩排行';

UPDATE resources SET name = '产品销售 TOP10', updated_at = now()
WHERE code = 'report_product_top10:view' AND path = '/reports?key=product-top10' AND name <> '产品销售 TOP10';

UPDATE resources SET name = '库存周转', updated_at = now()
WHERE code = 'report_inventory_turnover:view' AND path = '/reports?key=inventory-turnover' AND name <> '库存周转';

UPDATE resources SET name = '手术报台统计', updated_at = now()
WHERE code = 'report_surgery_stats:view' AND path = '/reports?key=surgery-stats' AND name <> '手术报台统计';

UPDATE resources SET name = '应收款项', updated_at = now()
WHERE code = 'report_receivables:view' AND path = '/reports?key=receivables' AND name <> '应收款项';

UPDATE resources SET name = '订单追溯', updated_at = now()
WHERE code = 'report_order_trace:view' AND path = '/reports?key=order-trace' AND name <> '订单追溯';

UPDATE resources SET name = '接口调用日志', updated_at = now()
WHERE code = 'api_log:view' AND path = '/api-call-logs' AND name <> '接口调用日志';

-- 2) 修复字典类型名称/描述（api_call_status）
UPDATE dict_types SET name = '接口调用状态', description = '接口调用 HTTP 状态', updated_at = now()
WHERE code = 'api_call_status' AND name <> '接口调用状态';

-- 3) 修复接口调用日志“状态”筛选项标签（平台默认，存在则更新）
UPDATE platform_filter_configs SET label = '状态', updated_at = now()
WHERE page_key = 'api-call-log' AND filter_key = 'status' AND label <> '状态';

-- 4) 修复经销商画像行内“查看”按钮标签
UPDATE platform_button_configs SET label = '查看画像', updated_at = now()
WHERE tenant_id IS NULL AND page_key = 'dealer-profile' AND scope = 'row' AND button_key = 'view' AND label <> '查看画像';