-- V82: 统一所有测试账号邮箱为 vinkinyu@163.com，便于接收/发送邮件
-- 业务 users 表：全部账号邮箱统一
UPDATE users
SET email = 'vinkinyu@163.com',
    updated_at = now()
WHERE email IS DISTINCT FROM 'vinkinyu@163.com';

-- 平台后台管理员账号邮箱统一
UPDATE platform_admin_users
SET email = 'vinkinyu@163.com',
    updated_at = now()
WHERE email IS DISTINCT FROM 'vinkinyu@163.com';
