-- v3.8.6 一个岗位可挂多个销售账号，移除 users(tenant_id, sales_position_id) 上的唯一索引
DROP INDEX IF EXISTS ux_users_position;
