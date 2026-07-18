-- =====================================================================
-- V6: 字典 / 系统参数 / 默认工作流初始化
-- 说明：
--   1) 全局字典（tenant_id = NULL）：ORDER_STATUS / ORDER_TYPE /
--      CONTRACT_STATUS / AUTH_TYPE / WAREHOUSE_TYPE / ADJ_CATEGORY /
--      ADJ_TYPE / PROMO_TYPE / USER_TYPE / USER_STATUS
--   2) 全局系统参数（scope='global', tenant_id = NULL）：KPI 阈值 /
--      单据编号前缀 / 审批超时
--   3) 默认工作流模板：合同 / 订单 / RMA / 库存调整 / 促销
--      每条流程包含 3 个默认节点（申请 / 审批 / 归档）。
--      系统级模板挂在 SYSTEM 租户（UUID = 00000000-0000-0000-0000-000000000000）下。
-- 兼容：Flyway 8.x + PostgreSQL 14
-- =====================================================================

-- ---------------------------------------------------------------------
-- 系统模板租户（用于挂载全局默认工作流；租户实例化时可复制）
-- ---------------------------------------------------------------------
INSERT INTO tenants (id, code, name, industry, status, attrs)
VALUES ('00000000-0000-0000-0000-000000000000',
        'SYSTEM', '系统模板租户', 'system', 'active',
        '{"primary_color":"#2C4B8E"}'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 1) 字典类型
-- ---------------------------------------------------------------------
INSERT INTO dict_types (tenant_id, code, name, description) VALUES
    (NULL, 'ORDER_STATUS',   '订单状态',       '订单生命周期状态枚举'),
    (NULL, 'ORDER_TYPE',     '订单类型',       '采购/急缺/自定义'),
    (NULL, 'CONTRACT_STATUS','合同状态',       '合同生命周期状态'),
    (NULL, 'AUTH_TYPE',      '授权类型',       '业务授权类型'),
    (NULL, 'WAREHOUSE_TYPE', '仓库类型',       '主仓/子仓/医院仓'),
    (NULL, 'ADJ_CATEGORY',   '调整方向',       '库存调整方向'),
    (NULL, 'ADJ_TYPE',       '调整类型',       '库存调整细类'),
    (NULL, 'PROMO_TYPE',     '促销类型',       'V1 启用 MOQ/FULL_REDUCTION'),
    (NULL, 'USER_TYPE',      '用户类型',       '厂商/经销商'),
    (NULL, 'USER_STATUS',    '用户状态',       '有效/锁定/停用')
ON CONFLICT (tenant_id, code) DO NOTHING;

-- ---------------------------------------------------------------------
-- 2) 字典项
-- ---------------------------------------------------------------------
-- ORDER_STATUS
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('DRAFT',                '草稿',        10),
    ('PENDING',              '待审批',      20),
    ('APPROVED',             '已审批',      30),
    ('ERP_APPROVING',        'ERP 审批中',  40),
    ('ERP_APPROVED',         'ERP 已审批',  50),
    ('ERP_SHIPPED',          'ERP 已发货',  60),
    ('RECEIVED',             '已收货',      70),
    ('COMPLETED',            '已完成',      80),
    ('REJECTED',             '已驳回',      90),
    ('RETURNED_TO_APPLICANT','退回申请人', 100),
    ('CANCELLED',            '已取消',     110)
) AS v(code, name, seq)
WHERE t.code = 'ORDER_STATUS' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- ORDER_TYPE
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('PURCHASE', '采购订单', 10),
    ('SHORTAGE', '急缺订单', 20),
    ('CUSTOM',   '自定义',   30)
) AS v(code, name, seq)
WHERE t.code = 'ORDER_TYPE' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- CONTRACT_STATUS
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('draft',         '草稿',       10),
    ('pending',       '待提交',     20),
    ('approving',     '审批中',     30),
    ('awaiting_sign', '待签署',     40),
    ('effective',     '生效中',     50),
    ('rejected',      '已驳回',     60),
    ('cancelled',     '已取消',     70),
    ('terminated',    '已终止',     80),
    ('expired',       '已到期',     90)
) AS v(code, name, seq)
WHERE t.code = 'CONTRACT_STATUS' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- AUTH_TYPE
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('ORDER',              '下单授权',       10),
    ('SALES_TO_HOSPITAL',  '销售至医院授权', 20),
    ('SALES_TO_SUB',       '销售至下级授权', 30),
    ('RMA',                '退换货授权',     40),
    ('MOVE',               '移库授权',       50),
    ('LOAN',               '借货授权',       60)
) AS v(code, name, seq)
WHERE t.code = 'AUTH_TYPE' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- WAREHOUSE_TYPE
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('main',     '主仓',     10),
    ('sub',      '子仓',     20),
    ('hospital', '医院仓',   30)
) AS v(code, name, seq)
WHERE t.code = 'WAREHOUSE_TYPE' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- ADJ_CATEGORY
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('INCREASE', '入库调整', 10),
    ('DECREASE', '出库调整', 20)
) AS v(code, name, seq)
WHERE t.code = 'ADJ_CATEGORY' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- ADJ_TYPE
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('换货入库',   '换货入库',   10),
    ('厂方换货',   '厂方换货',   20),
    ('盘盈',       '盘盈',       30),
    ('其他入库',   '其他入库',   40),
    ('过期报损',   '过期报损',   50),
    ('损坏报损',   '损坏报损',   60),
    ('盘亏',       '盘亏',       70),
    ('其他出库',   '其他出库',   80)
) AS v(code, name, seq)
WHERE t.code = 'ADJ_TYPE' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- PROMO_TYPE
INSERT INTO dict_items (type_id, code, name, seq, attrs)
SELECT id, v.code, v.name, v.seq, v.attrs::jsonb FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('MOQ',             '起订量满赠',  10, '{"v1_enabled":true}'),
    ('FULL_REDUCTION',  '满减',        20, '{"v1_enabled":true}'),
    ('GIFT',            '赠品',        30, '{"v1_enabled":false}'),
    ('BUNDLE',          '组合优惠',    40, '{"v1_enabled":false}')
) AS v(code, name, seq, attrs)
WHERE t.code = 'PROMO_TYPE' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- USER_TYPE
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('vendor', '厂商',     10),
    ('dealer', '经销商',   20)
) AS v(code, name, seq)
WHERE t.code = 'USER_TYPE' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- USER_STATUS
INSERT INTO dict_items (type_id, code, name, seq)
SELECT id, v.code, v.name, v.seq FROM dict_types t
CROSS JOIN LATERAL (VALUES
    ('active',   '有效',   10),
    ('locked',   '锁定',   20),
    ('disabled', '停用',   30)
) AS v(code, name, seq)
WHERE t.code = 'USER_STATUS' AND t.tenant_id IS NULL
ON CONFLICT (type_id, code) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3) 系统参数（scope=global，tenant_id = NULL）
-- ---------------------------------------------------------------------
INSERT INTO system_settings (scope, tenant_id, key, value_json, description) VALUES
    ('global', NULL, 'kpi.stock_report_rate.threshold',
        '{"warn":0.9,"error":0.8}'::jsonb,          '库存上报率阈值'),
    ('global', NULL, 'kpi.sales_report_rate.threshold',
        '{"warn":0.9,"error":0.8}'::jsonb,          '销售上报率阈值'),
    ('global', NULL, 'kpi.order_pass_rate.threshold',
        '{"warn":0.95,"error":0.85}'::jsonb,         '订单通过率阈值'),
    ('global', NULL, 'kpi.return_rate.threshold',
        '{"warn":0.05,"error":0.10}'::jsonb,         '退货率阈值'),
    ('global', NULL, 'doc.prefix.order',
        '"PO"'::jsonb,                               '采购订单编号前缀'),
    ('global', NULL, 'doc.prefix.contract',
        '"HT"'::jsonb,                               '合同编号前缀'),
    ('global', NULL, 'doc.prefix.receipt',
        '"RC"'::jsonb,                               '收货单编号前缀'),
    ('global', NULL, 'doc.prefix.sales_out',
        '"SO"'::jsonb,                               '销售出库单编号前缀'),
    ('global', NULL, 'doc.prefix.rma',
        '"RMA"'::jsonb,                              'RMA 单编号前缀'),
    ('global', NULL, 'doc.prefix.adjustment',
        '"ADJ"'::jsonb,                              '库存调整单编号前缀'),
    ('global', NULL, 'doc.prefix.stock_move',
        '"MV"'::jsonb,                               '移库单编号前缀'),
    ('global', NULL, 'doc.prefix.loan',
        '"LN"'::jsonb,                               '借货单编号前缀'),
    ('global', NULL, 'doc.prefix.promotion',
        '"PM"'::jsonb,                               '促销编号前缀'),
    ('global', NULL, 'approval.timeout.default_hours',
        '48'::jsonb,                                 '审批默认超时（小时）'),
    ('global', NULL, 'approval.timeout.contract_hours',
        '72'::jsonb,                                 '合同审批超时'),
    ('global', NULL, 'approval.timeout.order_hours',
        '24'::jsonb,                                 '订单审批超时'),
    ('global', NULL, 'approval.timeout.rma_hours',
        '48'::jsonb,                                 'RMA 审批超时'),
    ('global', NULL, 'approval.timeout.adjustment_hours',
        '24'::jsonb,                                 '库存调整审批超时'),
    ('global', NULL, 'approval.timeout.promotion_hours',
        '48'::jsonb,                                 '促销审批超时')
ON CONFLICT (scope, tenant_id, key) DO NOTHING;

-- ---------------------------------------------------------------------
-- 4) 默认工作流模板 + 节点（合同 / 订单 / RMA / 库存调整 / 促销）
-- ---------------------------------------------------------------------
INSERT INTO workflows (tenant_id, code, name, version, status)
VALUES
    ('00000000-0000-0000-0000-000000000000', 'WF_CONTRACT',   '合同审批流程',    1, 'active'),
    ('00000000-0000-0000-0000-000000000000', 'WF_ORDER',      '订单审批流程',    1, 'active'),
    ('00000000-0000-0000-0000-000000000000', 'WF_RMA',        'RMA 审批流程',    1, 'active'),
    ('00000000-0000-0000-0000-000000000000', 'WF_ADJUSTMENT', '库存调整审批流程',1, 'active'),
    ('00000000-0000-0000-0000-000000000000', 'WF_PROMOTION',  '促销审批流程',    1, 'active')
ON CONFLICT (tenant_id, code, version) DO NOTHING;

-- 合同流程节点
INSERT INTO workflow_nodes (workflow_id, code, name, node_type, assignee_strategy, timeout_hours, seq)
SELECT w.id, v.code, v.name, v.node_type, v.strategy, v.timeout_hours, v.seq
FROM workflows w
CROSS JOIN LATERAL (VALUES
    ('APPLY',   '提交申请', 'start',    'APPLICANT',       0,  10),
    ('APPROVE', '合同审批', 'approval', 'ROLE_CONTRACT_APPROVER', 72, 20),
    ('ARCHIVE', '归档',     'end',      'SYSTEM',          0,  30)
) AS v(code, name, node_type, strategy, timeout_hours, seq)
WHERE w.code = 'WF_CONTRACT' AND w.tenant_id = '00000000-0000-0000-0000-000000000000';

-- 订单流程节点
INSERT INTO workflow_nodes (workflow_id, code, name, node_type, assignee_strategy, timeout_hours, seq)
SELECT w.id, v.code, v.name, v.node_type, v.strategy, v.timeout_hours, v.seq
FROM workflows w
CROSS JOIN LATERAL (VALUES
    ('APPLY',   '提交订单', 'start',    'APPLICANT',           0,  10),
    ('APPROVE', '订单审批', 'approval', 'ROLE_ORDER_APPROVER', 24, 20),
    ('ARCHIVE', '归档',     'end',      'SYSTEM',              0,  30)
) AS v(code, name, node_type, strategy, timeout_hours, seq)
WHERE w.code = 'WF_ORDER' AND w.tenant_id = '00000000-0000-0000-0000-000000000000';

-- RMA 流程节点
INSERT INTO workflow_nodes (workflow_id, code, name, node_type, assignee_strategy, timeout_hours, seq)
SELECT w.id, v.code, v.name, v.node_type, v.strategy, v.timeout_hours, v.seq
FROM workflows w
CROSS JOIN LATERAL (VALUES
    ('APPLY',   '提交RMA',  'start',    'APPLICANT',         0,  10),
    ('APPROVE', 'RMA审批',  'approval', 'ROLE_RMA_APPROVER', 48, 20),
    ('ARCHIVE', '归档',     'end',      'SYSTEM',            0,  30)
) AS v(code, name, node_type, strategy, timeout_hours, seq)
WHERE w.code = 'WF_RMA' AND w.tenant_id = '00000000-0000-0000-0000-000000000000';

-- 库存调整流程节点
INSERT INTO workflow_nodes (workflow_id, code, name, node_type, assignee_strategy, timeout_hours, seq)
SELECT w.id, v.code, v.name, v.node_type, v.strategy, v.timeout_hours, v.seq
FROM workflows w
CROSS JOIN LATERAL (VALUES
    ('APPLY',   '提交调整', 'start',    'APPLICANT',         0,  10),
    ('APPROVE', '调整审批', 'approval', 'ROLE_ADJ_APPROVER', 24, 20),
    ('ARCHIVE', '归档',     'end',      'SYSTEM',            0,  30)
) AS v(code, name, node_type, strategy, timeout_hours, seq)
WHERE w.code = 'WF_ADJUSTMENT' AND w.tenant_id = '00000000-0000-0000-0000-000000000000';

-- 促销流程节点
INSERT INTO workflow_nodes (workflow_id, code, name, node_type, assignee_strategy, timeout_hours, seq)
SELECT w.id, v.code, v.name, v.node_type, v.strategy, v.timeout_hours, v.seq
FROM workflows w
CROSS JOIN LATERAL (VALUES
    ('APPLY',   '提交促销', 'start',    'APPLICANT',          0,  10),
    ('APPROVE', '促销审批', 'approval', 'ROLE_PROMO_APPROVER',48, 20),
    ('ARCHIVE', '归档',     'end',      'SYSTEM',             0,  30)
) AS v(code, name, node_type, strategy, timeout_hours, seq)
WHERE w.code = 'WF_PROMOTION' AND w.tenant_id = '00000000-0000-0000-0000-000000000000';
