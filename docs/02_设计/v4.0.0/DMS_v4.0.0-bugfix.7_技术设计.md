# DMS v4.0.0-bugfix.7 技术设计

**版本**: v4.0.0-bugfix.7  
**日期**: 2026-08-20

## 前端
- `CrudView.effectiveRowButtons()` 使用 Map 合并平台布局按钮和模块 `rowActions`，同 key 以模块配置覆盖默认配置。
- 修复通用行按钮 `cfg` 未定义导致部分模块“编辑”无效的问题。
- 销售订单 BOM 子件产品列展示 `productCode + productName`。
- 为基础资料和库存模块补充 `ResourceDetail` 只读路由，医院详情切换为通用只读详情。
- 采退只读页补充操作日志卡片。

## 后端
- `V4Calculator.allocateByAmount()` 使用分摊前固定基数，避免边扣减边改变权重。
- `allocatableBase()` 排除赠品、BOM 母件和 group header，整单折扣只分摊到真实计费行。
- `OperationLogAspect.captureCurrentEntity()` 补充 salesReturn、purchaseReturn、authorization、stockMove、inventoryAdjustment、surgeryReport、productLine、productBundle、productPrice、promotion 的表名映射。
- 产品价格列表支持 `productId` 逗号分隔批量查询，供 BOM 子件价格回填使用。

## 部署
- 前端：`frontend-vue/dist` 上传并解压到 `/opt/dms/test/frontend`，重启 `dms-test-nginx`。
- 后端：`backend/target/dms-backend.jar` 覆盖 `/opt/dms/test/backend/app.jar`，重建 `dms-test-backend`。
