-- V111: 修复 period_yyyymm 列类型漂移
-- V4/V5 将 period_yyyymm 建为 CHAR(6)，实体 @Column(length=6) 期望 varchar(6)，
-- Hibernate ddl-auto=validate 会报 wrong column type。CHAR(6) 会对不足 6 位的值补尾
-- 部空格，与 Java String 比较/唯一约束行为不一致，统一改为 VARCHAR(6)。
ALTER TABLE stocktakes
    ALTER COLUMN period_yyyymm TYPE VARCHAR(6);

ALTER TABLE rebate_previews
    ALTER COLUMN period_yyyymm TYPE VARCHAR(6);

ALTER TABLE rebate_settlements
    ALTER COLUMN period_yyyymm TYPE VARCHAR(6);

ALTER TABLE dealer_kpi_snapshots
    ALTER COLUMN period_yyyymm TYPE VARCHAR(6);
