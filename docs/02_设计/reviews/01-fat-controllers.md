# 评审报告：胖 Controller 与事务边界（项 2）

> 评审日期：2026-08-22（v4.1.1）
> 范围：`backend/src/main/java/com/dms`
> 方法：只读静态分析 + 测试运行，未改动业务代码

## 证据

| Controller | 行数 | 业务域 |
|---|---|---|
| order/controller/SalesReturnController | 758 | 销退 |
| order/controller/PurchaseOrderController | 754 | 采购 |
| org/controller/SalesPositionController | 542 | 销售岗位 |
| report/controller/BusinessReportController | 517 | 报表 |
| order/controller/SalesOrderController | 498 | 销售订单 |
| execution/controller/BizDocDetailController | 486 | 单据详情聚合 |
| surgery/controller/SurgeryReportController | 478 | 手术报台 |
| execution/controller/OrderApprovalExecutionController | 452 | 审批执行 |
| masterdata/controller/ProductPriceController | 434 | 产品价格 |
| compat/CompatAliasController | 407 | 兼容别名 |

全仓库 `@Transactional` 出现 568 处，存在事务标注分散的迹象。

## 风险

1. 500+ 行 Controller 混合了参数校验、状态机、价格/库存计算、DTO 拼装，导致：
   - 单测只能走完整 HTTP/MockMvc，无法对核心规则做毫秒级单测。
   - 事务边界容易落在 Controller（私有/自调用会使 `@Transactional` 失效）。
   - 重复逻辑散落（订单、采购、销退都有「校验→改状态→写明细→回写汇总」）。
2. 报表 Controller 517 行可能在 Web 层做了大量 SQL/聚合，难以缓存与优化。
3. 审批执行 Controller 与 ApprovalService 边界可能重叠。

## 建议（按影响力排序，不在本次执行）

- High：把 5 个 >480 行 Controller 的业务逻辑下沉到对应 Service，Controller 只做「鉴权入参 → 调 Service → 包装 ApiResponse」。每个 Service 方法对应一个可单测的用例。
- High：用 `@Transactional` 注解在 Service 公有方法上；审计 Controller/组件上的 `@Transactional` 并移除失效项。配合新增的 `V4CalculatorPromotionTest` 模式，把价格/折扣/赠品逻辑做成纯 Service 单测。
- Medium：抽取「单据状态流转」通用组件（submit/approve/cancel/close），订单/采购/销退复用，减少 3 份近似实现。
- Medium：报表查询下沉到只读 Repository/JPA 投影或查询服务，避免 Controller 直接拼数据。
- Low：对 >400 行 Controller 增加 ArchUnit 规则，新增 Controller 行数设上限。

## 与测试改进的关系

本次新增的 `V4CalculatorPromotionTest` 已证明「纯逻辑下沉后可做毫秒级单测」。胖 Controller 重构是把集成测试压力转为单元测试的关键前提。
