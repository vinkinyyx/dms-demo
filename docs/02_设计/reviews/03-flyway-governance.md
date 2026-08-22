# 评审报告：Flyway 迁移治理（项 5）

> 评审日期：2026-08-22（v4.1.1）
> 范围：`backend/src/main/resources/db/migration`

## 证据

- 迁移脚本总数：**109 个**（V1 ~ V109）。
- 最新：V104~V109（v4.x BOM/价格/审批修复）。
- `ALTER TABLE` 出现 **179** 处（大量增量改表）。
- `IF NOT EXISTS` 出现 **449** 处（脚本普遍做了幂等保护）。
- 启用 Flyway 跑测试时暴露一处真实 schema 漂移：实体 `EmailLog.durationMs` 映射为 bigint，而迁移建列为 int4（`application-test` 用 Hibernate `validate` 时会失败）。
- 早期脚本（V1~V8 与 V9 之间）存在大量「列已存在跳过」的幂等告警，说明历史上有过手工补表或脚本重排。

## 风险

1. 实体与迁移出现漂移（email_logs.duration_ms），意味着生产 schema 与代码映射存在不一致隐患；只是当前运行时未触发。
2. 「数字过百」本身不是问题，但缺乏命名分组与基线说明，新人难以判断哪些是 v4 重构、哪些是历史热修。
3. 无法确认是否有人改过已发布的迁移（幂等 `IF NOT EXISTS` 让这种改动不易被发现）。

## 建议（不在本次执行）

- High：修复 `email_logs.duration_ms` 实体/迁移类型不一致——加一个 V110 把列改为 bigint（或把实体改为 Integer），并在测试 profile 用 `ddl-auto: validate` + Flyway 长期开启，防止再次漂移。本次为让测试跑通，测试 profile 暂用 `ddl-auto: none`。
- Medium：为迁移加一份 `db/migration/README.md`，按版本区间标注（V1~V70 历史基线、V72~V73 审批、V96~V109 v4 计价/BOM）。
- Medium：在 CI 增加一个 job：用干净库跑 `flyway migrate` + Hibernate validate，守护「迁移可重放、与实体一致」。
- Low：约定今后改表用新的递增脚本，禁止修改已发布脚本；补丁脚本命名加业务前缀（如 V110__fix_email_log_duration_ms.sql）。
