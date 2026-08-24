# ERP → DMS 销售出库回传接口

> 版本：v4.2.7（R9）
> 适用：外部 ERP/WMS 完成实物出库后，把出库结果回传 DMS，DMS 据此生成销售出库单、扣减库存/批号/序列号、推进订单状态机。
> Base URL：`http(s)://<host>/open/api/erp`
> 内容类型：`application/json; charset=utf-8`

---

## 1. 接口概述

| 项 | 说明 |
|----|------|
| POST `/sales-outbounds` | 接收 ERP 出库回传，生成 DMS 销售出库单。支持幂等。 |
| GET `/sales-outbounds/{idempotencyKey}` | 按幂等键查询某次回调的处理结果，供 ERP 排障与对账。 |

特性：

- 复用 DMS 对外接口 HMAC-SHA256 鉴权（`/open/api/**` 过滤器），不单独登录。
- 报文一律使用**业务编码**（订单号、产品编码、仓库编码），由 DMS 解析内部 ID。
- 内部复用 `V4ErpService.receiveOutbound`，已实现：幂等、`sales_outs/sales_out_lines` 写入、库存/批号/序列号、订单状态机刷新、红字（`direction=RED`）回写。
- 支持蓝字销售出库（FORWARD）与红字销退出库（RED）。
- 支持完整出库与部分出库：同一订单可多次回传，累计数量不得超过「订单数量 − 已关闭数量 − 已出库数量」。

---

## 2. 鉴权

所有请求必须携带以下请求头：

| 请求头 | 必填 | 说明 |
|--------|------|------|
| `X-App-Key` | 是 | 应用标识，由 DMS 分配（默认示例 `dms-erp-app`）。 |
| `X-Timestamp` | 是 | 请求发起时间的**毫秒级**时间戳（UTC）。允许与服务端偏差 ±5 分钟。 |
| `X-Nonce` | 是 | 随机串，建议 UUID，防重放。 |
| `X-Signature` | 是 | 签名值，小写 hex。 |

### 2.1 获取 app_key / app_secret

DMS 管理员在「平台后台 → 对外接口应用」创建应用，或由迁移脚本为 `default` 租户预置：

- app_key：`dms-erp-app`
- app_secret：`0a1b2c3d4e5f60718293a4b5c6d7e8f9`（示例，生产请重置）
- app_name：`ERP标准对接应用`，system：`ERP`，status：`active`

可在应用上配置 `allowed_ips`（逗号分隔）作为 IP 白名单；为空则不限制。

### 2.2 签名算法

```
bodyHash   = sha256Hex( rawRequestBodyBytes )   // 空 body 用空字节数组的 sha256
signString = HTTP_METHOD + "\n" + REQUEST_PATH + "\n" + X-Timestamp + "\n" + X-Nonce + "\n" + bodyHash
signature  = hex( HMAC_SHA256( appSecret, signString ) ).toLowerCase()
```

注意：

- `REQUEST_PATH` 为请求 URI 的 path 部分（含上下文路径，如 `/dms/open/api/erp/sales-outbounds`），与实际请求完全一致，不含 query string。
- body 必须是**参与签名的原始字节**，服务端按收到的原始 body 计算 hash，请勿在签名后再修改 body。
- 所有字符串按 UTF-8 编码。
- 签名比对采用常量时间比较。

### 2.3 签名示例（Node.js 伪代码）

```js
const crypto = require('crypto');
const method = 'POST';
const path = '/open/api/erp/sales-outbounds';
const ts = Date.now().toString();
const nonce = crypto.randomUUID();
const body = JSON.stringify(payload);
const bodyHash = crypto.createHash('sha256').update(body, 'utf8').digest('hex');
const signString = [method, path, ts, nonce, bodyHash].join('\n');
const signature = crypto.createHmac('sha256', appSecret).update(signString, 'utf8').digest('hex');
// headers: X-App-Key / X-Timestamp / X-Nonce / X-Signature
```

curl 示例见 §7。

---

## 3. POST 接收出库回传

`POST /open/api/erp/sales-outbounds`

### 3.1 请求体字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| requestId | string(128) | 否 | ERP 请求流水号，仅用于日志追溯。 |
| idempotencyKey | string(128) | 是 | 幂等键：ERP 出库单号或 UUID。重复请求返回首次结果。 |
| sourceOrderCode | string | 二选一 | DMS 销售订单号；红字时为销退单号。 |
| sourceOrderId | long | 二选一 | DMS 订单内部 ID，优先使用。 |
| direction | string | 否 | `FORWARD`（默认，销售出库）/ `RED`（红字销退出库）。 |
| erpOutboundNo | string(128) | 是 | ERP 出库单号。 |
| warehouseCode | string | 否 | 仓库编码；缺省回退订单仓库/默认仓。 |
| outboundDate | date | 否 | `yyyy-MM-dd`，默认今天。 |
| remark | string | 否 | 备注。 |
| lines | array | 是 | 出库明细，至少 1 行。 |

lines[]：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sourceOrderLineId | long | 否 | DMS 订单行 ID；同产品多行时建议传入以精确定位。 |
| productCode | string | 二选一 | 产品编码。 |
| productId | long | 二选一 | 产品内部 ID。 |
| qty | decimal | 是 | 本次出库数量，必须 > 0。 |
| batchNo | string | 否 | 批号。 |
| serialNo | string | 否 | 序列号（序列号管理产品建议填写）。 |
| unitPrice | decimal | 否 | 出库单价；不传取订单行价格。 |
| remark | string | 否 | 行备注。 |

### 3.2 响应体（data）

`ErpOutboundResult`：

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 0 成功；其余为错误码。 |
| message | string | 结果说明。 |
| salesOutId | long | DMS 销售出库单 ID。 |
| salesOutCode | string | DMS 销售出库单号（GI/GIR 开头）。 |
| idempotent | bool | 是否幂等命中。 |
| direction | string | FORWARD/RED。 |
| processedLines | int | 成功处理行数。 |
| failedLines | array | 失败行：`{lineNo, product, reason}`。 |
### 3.3 回执码

| code | 含义 |
|------|------|
| 0 | 成功 |
| 40001 | 参数校验失败（字段缺失/格式/为空等，由 `@Valid` 触发） |
| 40002 | 缺少必要参数 |
| 40006 | 业务规则校验失败（超量、订单状态不符等） |
| 40100 | 未识别租户 / 鉴权失败（过滤器返回 401 HTTP） |
| 40301 | 应用禁用 / IP 不在白名单（403 HTTP） |
| 40401 | 订单/产品/仓库/回调记录不存在 |
| 50000 | 系统内部错误 |

> 鉴权失败由过滤器在进入 Controller 前返回 HTTP 401/403，响应体同样为 `ApiResponse`。

### 3.4 幂等

- 以 `idempotencyKey` + 租户为唯一键（`erp_outbound_callbacks` 上有唯一索引）。
- 首次成功后，相同 `idempotencyKey` 的请求直接返回首次结果（`idempotent=true`），不会重复扣库存或重复生成出库单。
- 建议 `idempotencyKey` 直接使用 ERP 出库单号；若一个 ERP 单需要分多次回传，请为每次生成不同的 key（如 `ERP单号-批次序号`）。

### 3.5 状态机影响

- 蓝字：订单 `APPROVED` →（部分）`PARTIAL_OUTBOUND` →（全部出完）`COMPLETED`。
- 红字：销退单 `APPROVED` →（部分）`PARTIAL_RED_OUTBOUND` →（全部出完）`COMPLETED`。
- 只有 `APPROVED`/`PARTIAL_OUTBOUND`（蓝字）或 `APPROVED`/`PARTIAL_RED_OUTBOUND`（红字）的单据可接收回传；其他状态返回 40006。
- 红字回传会回写原蓝字出库行的 `returned_qty`。

### 3.6 数量规则

- 每行 `qty` 必须 > 0。
- 单订单行累计出库不得超过 `qty - closed_qty - 已出库累计`，超出返回 40006 并在 `failedLines` 标注待出库数量。
- 报文中同一产品在订单中存在多个可出库行时，必须传 `sourceOrderLineId` 精确定位，否则返回参数错误。
- BOM 母件行（`line_level=PARENT` / `is_group_header`）不能直接出库。

---

## 4. GET 查询回调结果

`GET /open/api/erp/sales-outbounds/{idempotencyKey}`

路径参数：`idempotencyKey`（幂等键）。

成功返回 `ErpOutboundResult`（`idempotent=true`，含 `salesOutId/salesOutCode/direction/processedLines`）；
未找到返回 `40401`。

---

## 5. 完整示例

### 5.1 FORWARD 完整出库

请求：

```json
{
  "requestId": "ERP-REQ-20260824-0001",
  "idempotencyKey": "ERP-OUT-20260824-0001",
  "sourceOrderCode": "SO202608240001",
  "direction": "FORWARD",
  "erpOutboundNo": "ERP-OUT-20260824-0001",
  "warehouseCode": "WH001",
  "outboundDate": "2026-08-24",
  "lines": [
    { "productCode": "PRD-A001", "qty": 10, "batchNo": "B20260824", "serialNo": "SN0001" },
    { "productCode": "PRD-A002", "qty": 5 }
  ]
}
```

成功响应：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "code": 0,
    "message": "OK",
    "salesOutId": 1024,
    "salesOutCode": "GI202608240001",
    "idempotent": false,
    "direction": "FORWARD",
    "processedLines": 2,
    "failedLines": []
  },
  "requestId": ""
}
```

### 5.2 部分出库

只回传订单中某产品的部分数量（例如应出 10、本次出 4）：

```json
{
  "idempotencyKey": "ERP-OUT-20260824-0002",
  "sourceOrderCode": "SO202608240001",
  "erpOutboundNo": "ERP-OUT-20260824-0002",
  "lines": [ { "productCode": "PRD-A001", "qty": 4 } ]
}
```

成功后订单状态变为 `PARTIAL_OUTBOUND`；剩余数量可在后续回调中继续出库（使用新的 idempotencyKey）。

### 5.3 RED 红字出库

```json
{
  "idempotencyKey": "ERP-RED-20260824-0001",
  "sourceOrderCode": "SR202608240001",
  "direction": "RED",
  "erpOutboundNo": "ERP-RED-20260824-0001",
  "lines": [
    { "sourceOrderLineId": 8801, "productCode": "PRD-A001", "qty": 2 }
  ]
}
```

红字成功响应中 `salesOutCode` 以 `GIR` 开头，`direction=RED`。

### 5.4 幂等响应

使用相同 `idempotencyKey` 再次请求，返回首次结果且不重复处理：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "code": 0,
    "message": "OK",
    "salesOutId": 1024,
    "salesOutCode": "GI202608240001",
    "idempotent": true,
    "direction": "FORWARD",
    "processedLines": 2,
    "failedLines": []
  }
}
```

### 5.5 超量失败

某行 `qty=999999` 超过待出库数量：

```json
{
  "code": 40006,
  "message": "出库回传存在 1 行校验失败",
  "data": {
    "code": 40006,
    "message": "出库回传存在 1 行校验失败",
    "idempotent": false,
    "direction": "FORWARD",
    "processedLines": 0,
    "failedLines": [
      { "lineNo": 1, "product": "PRD-A001", "reason": "出库数量 999999 超过待出库数量 6" }
    ]
  }
}
```
### 5.6 字段缺失

`lines` 为空或缺 `idempotencyKey` 时返回 40001，例如：

```json
{ "code": 40001, "message": "lines 不能为空", "data": null }
```

### 5.7 鉴权失败

```json
{ "code": 401401, "message": "签名校验失败", "data": null }
```

---

## 6. curl 签名示例

```bash
APP_KEY="dms-erp-app"
APP_SECRET="0a1b2c3d4e5f60718293a4b5c6d7e8f9"
BASE="http://43.128.145.141"
PATH_API="/open/api/erp/sales-outbounds"
TS=$(($(date +%s)*1000))
NONCE=$(cat /proc/sys/kernel/random/uuid)
BODY='{"idempotencyKey":"ERP-OUT-001","sourceOrderCode":"SO202608240001","erpOutboundNo":"ERP-OUT-001","lines":[{"productCode":"PRD-A001","qty":1}]}'
BODY_HASH=$(printf '%s' "$BODY" | sha256sum | awk '{print $1}')
SIGN_STR=$(printf 'POST\n%s\n%s\n%s\n%s' "$PATH_API" "$TS" "$NONCE" "$BODY_HASH")
SIG=$(printf '%s' "$SIGN_STR" | openssl dgst -sha256 -hmac "$APP_SECRET" | awk '{print $2}')
curl -s -X POST "$BASE$PATH_API" \
  -H "Content-Type: application/json" \
  -H "X-App-Key: $APP_KEY" \
  -H "X-Timestamp: $TS" \
  -H "X-Nonce: $NONCE" \
  -H "X-Signature: $SIG" \
  -d "$BODY"
```

> 若 DMS 部署在子路径（如生产 `/dms`），`PATH_API` 需包含该前缀，与实际 URL 一致。

---

## 7. 联调 Checklist

- [ ] 已在 DMS 后台创建/启用 ERP 应用，拿到 app_key/app_secret，必要时配置 IP 白名单。
- [ ] 服务器时间与 DMS 同步，偏差 ≤ 5 分钟。
- [ ] 签名 path 与实际请求 path（含上下文前缀）完全一致。
- [ ] body 参与签名后未被再加工（字段顺序/空白变化会导致 bodyHash 不一致）。
- [ ] 订单存在、状态为 `APPROVED` 或 `PARTIAL_OUTBOUND`（红字为对应状态）。
- [ ] 产品编码、仓库编码在该租户下存在。
- [ ] 出库数量不超过待出库数量；同产品多行时传 `sourceOrderLineId`。
- [ ] 序列号管理产品传 `serialNo`，批号管理产品传 `batchNo`。
- [ ] 重试时复用同一个 `idempotencyKey`；分批出库使用不同 key。
- [ ] 收到非 0 code 时按 message/failedLines 修正；偶发 5xx 可用相同 key 安全重试。
- [ ] 使用 `GET /sales-outbounds/{key}` 核对历史回调结果。

---

## 8. 数据落库与实现说明

- 出库主表 `sales_outs`（`business_type='ERP'`、`is_red`、`source_order_id`、`erp_outbound_no`、`idempotency_key`、`callback_payload`）。
- 明细表 `sales_out_lines`，并写 `sales_out_batches`/`sales_out_batch_lines`。
- 回调记录表 `erp_outbound_callbacks`（V113 新增 `request_id`、`failed_lines`）。
- 回调记录由核心服务 `V4ErpService.receiveOutbound` 写入；本标准接口在成功后仅 `UPDATE` 回填 `request_id`，**不重复插入** callback。
- 不改动保留接口 `/api/v4/erp/outbound-callbacks` 与 `/simulate-ship`（前端演示在用）。