# DMS Mock Server 配置说明

本目录挂载到 `wiremock/wiremock:3.3.1` 容器中，作为外部第三方服务的 Mock 桩，用于本地开发与演示环境。所有 JSON 文件遵循 WireMock 3.x 的 `mappings` 格式。

## 服务用途

| 目录 | 模拟对象 | 说明 |
| --- | --- | --- |
| `wechat/` | 微信开放平台 / 小程序扫码 | 生成扫码二维码、模拟扫码回调（已绑定 / 新用户） |
| `ca/` | e签宝电子签章 | 合同签章流水号 + 签署完成回调 |
| `erp/` | 通用 REST ERP | 订单推送、产品/价格同步 |
| `wecom/` | 企业微信机器人 | Webhook 消息推送 |
| `feishu/` | 飞书机器人 | Webhook 消息推送 |

## 端口

WireMock 监听 **9090**，Backend 通过环境变量 `MOCK_BASE_URL=http://mock-server:9090` 调用。

## 请求路径约定（示例）

- `POST /mocks/wechat/qr` → 返回二维码 scene + 图片地址
- `GET  /mocks/wechat/callback/success` → 已绑定用户回调
- `GET  /mocks/wechat/callback/need-bind` → 新用户需绑定回调
- `POST /mocks/ca/sign` → 签章接口
- `POST /mocks/erp/orders/push` → ERP 推送订单
- `GET  /mocks/erp/products/sync` → ERP 拉取产品
- `POST /mocks/wecom/webhook` → 企微 Webhook
- `POST /mocks/feishu/webhook` → 飞书 Webhook

## 修改与生效

- 修改本目录任意 JSON 后，重启容器：`docker compose restart mock-server`
- 也可用 WireMock 管理接口热更新：`curl -X POST http://localhost:9090/__admin/mappings/reset`
