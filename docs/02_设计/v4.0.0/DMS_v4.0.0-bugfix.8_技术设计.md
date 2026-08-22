# DMS v4.0.0-bugfix.8 技术设计

**版本**: v4.0.0-bugfix.8  
**日期**: 2026-08-21

## 1. 促销计算
- `V4Calculator` 在每次订单试算前清除已生成的 `isGift=true` 行，再按活动规则重新生成，保证幂等和动态更新。
- `EVERY_N` 规则兼容 `thresholdQty/buyQty/everyN` 与 `giftQty/gift_qty` 等字段；当门槛缺失时使用 `everyN`。
- 赠品按赠品 SKU 聚合，避免多个规则赠送同一产品时出现重复行。
- 满减和整单折扣的分摊基数排除 BOM 母件和赠品；赠品保留标准价用于成本/展示，但收费金额为 0。

## 2. 订单试算
- 新增 `POST /api/sales-orders/preview`，调用 `V4OrderService.previewSalesOrder` 返回明细和汇总金额。
- `OrderCreate.vue` 在产品、数量、行折扣、整单折扣和经销商变化后调用试算接口。
- 页面只提交用户可编辑的顶层行；BOM 子件和赠品由后端展开，避免前端篡改。

## 3. BOM 出库
- `V4ErpService.simulateShip` 与审批自动建单均过滤 `line_level='PARENT'`。
- 出库完成状态刷新只统计非 BOM 母件行，避免母件无实物导致永远部分出库。
- 赠品作为实物行参与出库，金额为 0。

## 4. 销退金额
- `SalesReturnController` 从 `sales_out_lines.final_amount / shipped_qty` 取原出库行平摊含税单价。
- 创建和更新销退单均按原出库行价格重新计算总额，忽略前端提交的金额。
- 详情返回每行 `unitPrice/finalAmount/subtotal`，前端只读和编辑页展示平摊单价、行总价和汇总金额。

## 5. 自动化与清理
- 深度 E2E 使用 `automation_test/e2e/specs/12-orders-promo-bom-return.spec.js`，文件名包含 `orders`，确保 `npm run test:all -- --module=orders` 会自动执行。
- 订单详情页断言使用真实路由 `/orders/:id`，并通过稳定 class `order-lines-card` 验证赠品标签，避免错误路由或宽松选择器造成假通过。
- 新增 `automation_test/e2e/helpers/db-cleanup.*`，测试结束后按本次创建的订单、销退和出库 ID 事务级清理审批、出库回调、明细和表头，不依赖仅草稿可删的业务接口。
- 清理 helper 从 `DMS_DEPLOY_PASSWORD` 读取测试服务器密码，不写入代码；执行后必须回读最新列表确认无新增测试残留。
