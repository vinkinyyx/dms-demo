# DMS v3.12.3 P2交付测试报告

> 测试日期：2026-08-14
> 测试环境：http://43.128.145.141/
> 健康检查：`{"status":"UP","groups":["liveness","readiness"]}`

## 1. 交付范围
- 修复收货单列表 `supplier_name` 别名错误，销售出库不再读取不存在字段。
- 修复移动端扫码收货/库存扫码/收货确认中文乱码和快捷入口文案。
- 完成 MFA/TOTP、登录限流、库存盘点、报表订阅、效期预警、序列号追溯验证。
- 增加变更类请求防重复提交、PC命令面板、主数据批量删除、日志保留清理任务。
- 统一测试环境文档地址为 http://43.128.145.141/。

## 2. 构建结果
| 构建项 | 命令 | 结果 |
|---|---|---|
| 后端 | `mvn -f backend/pom.xml -DskipTests clean package` | 通过 |
| 业务前台 | `npm --prefix frontend-vue run build` | 通过 |
| 平台后台 | `npm --prefix admin-vue run build` | 通过 |

## 3. 自动化测试结果
| 测试集 | 范围 | 结果 |
|---|---|---|
| P2 API 非限流 | MFA、库存盘点、报表订阅、效期、追溯 | 6 passed / 1 deselected |
| P2 API 限流 | 登录爆破限流 | 1 passed |
| P0/登录/入口 | P0、L1入口、登录 | 111 passed, 7 xfailed, 4 xpassed |
| 宽回归 | L2-L5、移动端、安全、平台日志 | 282 passed, 7 skipped, 14 xfailed, 18 xpassed |
| 业务模块 | 产品、订单、库存、合同、审批、报表、促销、报台、权限、平台 | 127 passed, 27 skipped |
| 安全/集成 | 集成链路、高级安全 | 21 passed, 5 skipped, 2 xfailed |
| 浏览器深度E2E | 登录、报表订阅CRUD、盘点、MFA、效期、追溯、移动扫码、控制台错误 | 16 passed, 0 failed |

## 4. 关键缺陷复测
| 缺陷 | 复测结果 |
|---|---|
| 移动端扫码页中文显示为???? | 已修复，E2E显示“扫码收货/库存扫码查询/待收货单/库存结果” |
| `/api/receipts?status=DRAFT` 返回 `Unknown alias [supplier_name]` | 已修复，E2E移动扫码页通过 |
| 移动端扫码页未登录时无法验证 | 已修正E2E为独立移动上下文真实登录 |

## 5. 截图与产物
- E2E结果：`tools/p2-e2e/results/p2-e2e-results.json`
- E2E截图：`tools/p2-e2e/results/*.png`
- Pytest HTML报告：`automation_test/reports/report.html`

## 6. 结论
v3.12.3 P2补强已构建、部署并通过自动化测试与浏览器深度E2E。本轮自动化共执行 564 条断言/用例级检查，0 个失败；skip/xfail 均为历史已知或前置数据不满足项，不影响本次P2交付结论。
