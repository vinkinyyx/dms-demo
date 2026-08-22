# 评审报告：JPA 与 MyBatis-Plus 边界（项 4）

> 评审日期：2026-08-22（v4.1.1）
> 范围：`backend/src/main/java/com/dms`

## 证据

- JPA Repository（extends JpaRepository/CrudRepository 等）：**82 个**。
- MyBatis 映射：**仅 1 个** —— `mapper/OperationLogMapper.java`（@Mapper/BaseMapper），用于操作日志写入。
- 未发现 MyBatis Mapper XML 业务查询（resources 下只有 `logback-spring.xml`）。
- 配置层同时存在 `spring-boot-starter-data-jpa` 与 `MybatisPlusConfig`。

## 结论

**项 4 基本是伪问题，风险很低。** 数据访问主路径是 Spring Data JPA + Hibernate；MyBatis-Plus 仅用于操作日志这一个高频写入、低业务复杂度的旁路。两者边界清晰，不存在「同一业务表两套 ORM」的混乱。

## 建议（不在本次执行）

- Low：确认 `OperationLogMapper` 是否为性能优化（批量/异步写日志）而保留。若是，在类注释中写明「仅操作日志旁路，业务查询走 JPA」。
- Low：`MybatisPlusConfig` 若只服务这一个 mapper，可加注释限定扫描包，避免后人误以为项目以 MyBatis 为主。
- 无需做 ORM 迁移或统一；保持现状即可。
