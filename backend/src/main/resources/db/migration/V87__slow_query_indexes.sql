-- V87: 慢查询治理 - 补充高频查询复合索引（NF-01）
-- 全部使用 IF NOT EXISTS，可安全重复执行

-- 销售/采购订单列表：租户+状态+时间倒序
CREATE INDEX IF NOT EXISTS idx_orders_tenant_status_created
  ON orders (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_tenant_status_created
  ON purchase_orders (tenant_id, status, created_at DESC);

-- 审批实例：租户+状态+时间
CREATE INDEX IF NOT EXISTS idx_approval_instances_tenant_status
  ON approval_instances (tenant_id, status, created_at DESC);

-- 邮件日志：租户+状态+发送时间
CREATE INDEX IF NOT EXISTS idx_email_logs_tenant_status_sent
  ON email_logs (tenant_id, status, sent_at DESC);

-- 用户登录日志：用户+时间
CREATE INDEX IF NOT EXISTS idx_user_login_logs_user_time
  ON user_login_logs (user_id, at_time DESC);

-- 接口调用日志：租户+时间倒序
CREATE INDEX IF NOT EXISTS idx_api_call_log_tenant_time
  ON api_call_log (tenant_id, started_at DESC);

-- 库存批次效期查询：租户+效期（B6 预警使用，部分索引）
CREATE INDEX IF NOT EXISTS idx_inventory_tenant_exp
  ON inventory (tenant_id, exp_date)
  WHERE qty > 0 AND exp_date IS NOT NULL;

-- 通知：租户+用户+已读+时间
CREATE INDEX IF NOT EXISTS idx_notifications_tenant_user_read
  ON notifications (tenant_id, user_id, is_read, created_at DESC);

-- 异步任务：租户+类型+状态
CREATE INDEX IF NOT EXISTS idx_async_task_tenant_type_status
  ON async_task (tenant_id, task_type, status, id DESC);