-- ---------------------------------------------------------------------
-- V146: 定时邮件发送开关（运行时可在平台后台切换，无需重启）
-- 存储于 system_settings：scope='global'、tenant_id=NULL，value_json 为布尔
--   mail.schedule.enabled          定时邮件总开关（关闭后所有定时自动邮件全部停止）
--   mail.schedule.report.enabled   报表订阅邮件（每日 08:00，CSV 附件）
--   mail.schedule.approval.enabled 审批超时提醒（每日 09:00）
-- 缺失配置时代码回退 yml：dms.mail.enabled（默认 true），保证向后兼容。
-- 幂等：仅当 key 不存在时插入，匹配真实唯一约束 (scope, tenant_id, key)。
-- ---------------------------------------------------------------------
INSERT INTO system_settings (scope, tenant_id, key, value_json, description)
SELECT 'global', NULL, v.key, CAST(v.value AS jsonb), v.description
FROM (VALUES
    ('mail.schedule.enabled',          'true', '定时邮件总开关：关闭后所有定时自动发送的邮件全部停止'),
    ('mail.schedule.report.enabled',   'true', '报表订阅邮件开关：每日 08:00 自动发送订阅报表（CSV 附件）'),
    ('mail.schedule.approval.enabled', 'true', '审批超时提醒开关：每日 09:00 自动发送审批超时提醒邮件')
) AS v(key, value, description)
WHERE NOT EXISTS (
    SELECT 1 FROM system_settings s
    WHERE s.scope = 'global' AND s.tenant_id IS NULL AND s.key = v.key
);
