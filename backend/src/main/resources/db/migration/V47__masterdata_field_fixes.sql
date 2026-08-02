-- v3.8.4 修复主数据编辑字段与表单不一致导致的“保存不生效”问题
-- 1. 产品分类、区域增加排序号字段（前端表单已有 sortOrder）
ALTER TABLE product_categories ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE regions            ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;
-- 2. 角色增加类型（system/custom），前端表单已有 type
ALTER TABLE roles ADD COLUMN IF NOT EXISTS role_type VARCHAR(16) NOT NULL DEFAULT 'custom';
