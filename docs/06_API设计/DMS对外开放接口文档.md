# DMS 对外开放接口文档（Open API）

**版本**: v3.8.3
**最后更新**: 2026-08-02
**适用范围**: 外部系统（ERP/WMS/HR/UDI/CA/第三方平台）调用 DMS 创建单据

---

## 1. 概述

DMS 对外开放一组 RESTful 接口，供外部系统以 **HMAC-SHA256 签名**方式调用，无需用户名密码登录。当前开放：

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 创建销售订单 | POST | `/open/api/sales-orders` | 在 DMS 中创建一张销售订单（草稿） |
| 创建采购订单 | POST | `/open/api/purchase-orders` | 在 DMS 中创建一张采购订单（草稿） |

未来新增接口将沿用同一套鉴权与签名规则。

### 1.1 环境地址

| 环境 | Base URL |
|------|----------|
| 测试环境 | `http://8.133.193.238:8082` |
| 生产环境 | 由实施方提供 |

### 1.2 数据格式
- 请求与响应均为 `application/json; charset=UTF-8`。
- 日期格式：`yyyy-MM-dd`（如 `2026-08-10`）。
- 金额/数量：数字，保留两位小数，不加千分位。
- 主数据统一使用 **编码（code）** 传参，而非内部 ID（产品也可传 productId 作为备选）。

---

## 2. 鉴权与签名

### 2.1 接入凭据
每个对接系统由 DMS 管理员分配一对凭据（存于 `open_app` 表）：

- `appKey`：应用标识，明文传输，放在请求头。
- `appSecret`：应用密钥，**绝不传输**，仅用于客户端本地计算签名。

> 测试环境默认应用：appKey = `dms-demo-app`，appSecret = `8c39b1f7e2a44d6b9f0a1c2d3e4f5a6b`（仅测试用，生产请重置）。

### 2.2 请求头
每个请求必须携带以下请求头：

| 请求头 | 必填 | 说明 |
|--------|------|------|
| `X-App-Key` | 是 | 应用标识 |
| `X-Timestamp` | 是 | 当前毫秒时间戳（UTC），与服务器偏差不得超过 ±5 分钟 |
| `X-Nonce` | 是 | 随机字符串，建议 UUID，用于防重放 |
| `X-Signature` | 是 | 按下方规则计算的 HMAC-SHA256 签名（小写 hex） |
| `Content-Type` | 是 | 固定 `application/json` |

### 2.3 签名算法

**待签名字符串 signString**（字段间用换行符 `\n` 连接）：

```
HTTP_METHOD + "\n" + REQUEST_PATH + "\n" + X-Timestamp + "\n" + X-Nonce + "\n" + sha256Hex(body)
```

- `HTTP_METHOD`：大写，如 `POST`。
- `REQUEST_PATH`：路径部分（不含域名与 query），如 `/open/api/sales-orders`。
- `sha256Hex(body)`：请求体原始字节的 SHA-256 摘要（小写 hex）。GET 等无 body 时为空字符串的摘要。

**签名**：

```
signature = lower( HMAC_SHA256(appSecret, signString) )
```

### 2.4 签名示例（伪代码）

**Python**
```python
import hashlib, hmac, time, uuid, json, requests

APP_KEY = "dms-demo-app"
APP_SECRET = "8c39b1f7e2a44d6b9f0a1c2d3e4f5a6b"
BASE = "http://8.133.193.238:8082"

def call(method, path, body_obj):
    body = json.dumps(body_obj, ensure_ascii=False)
    ts = str(int(time.time() * 1000))
    nonce = uuid.uuid4().hex
    body_hash = hashlib.sha256(body.encode()).hexdigest()
    sign_string = f"{method}\n{path}\n{ts}\n{nonce}\n{body_hash}"
    signature = hmac.new(APP_SECRET.encode(), sign_string.encode(), hashlib.sha256).hexdigest()
    headers = {
        "X-App-Key": APP_KEY, "X-Timestamp": ts, "X-Nonce": nonce,
        "X-Signature": signature, "Content-Type": "application/json"
    }
    return requests.request(method, BASE + path, headers=headers, data=body.encode())
```

**Java**
```java
String bodyHash = sha256Hex(body.getBytes(StandardCharsets.UTF_8));
String signString = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyHash;
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
String signature = HexFormat.of().formatHex(mac.doFinal(signString.getBytes(StandardCharsets.UTF_8)));
```

**JavaScript (Node.js)**
```javascript
const crypto = require('crypto');
const bodyHash = crypto.createHash('sha256').update(body).digest('hex');
const signString = `${method}\n${path}\n${timestamp}\n${nonce}\n${bodyHash}`;
const signature = crypto.createHmac('sha256', appSecret).update(signString).digest('hex');
```

### 2.5 鉴权失败响应
HTTP 状态码 401/403，响应体：

```json
{ "code": 40501, "message": "签名校验失败", "requestId": "" }
```

常见原因：缺少鉴权头、时间戳超差、appKey 无效、应用被禁用、来源 IP 不在白名单、签名计算错误（注意 body 必须与发送的字节完全一致，建议用同一份序列化结果计算 hash 与发送）。

---

## 3. 统一响应结构

所有接口返回统一信封：

```json
{
  "code": 0,
  "message": "OK",
  "data": { },
  "requestId": "bbd9b85ca4be408584852533f068034d"
}
```

- `code = 0` 表示业务成功；非 0 表示业务失败（HTTP 仍可能为 200）。
- `message`：结果描述。
- `data`：业务数据。
- `requestId`：链路追踪 ID，排查问题时请提供。

常用错误码：

| code | 含义 |
|------|------|
| 0 | 成功 |
| 40001 | 参数校验失败（如必填项为空、明细为空） |
| 40401 | 主数据不存在（经销商/供应商/仓库/产品编码错误） |
| 40501 | 鉴权失败（HTTP 401） |
| 40300 | 无权限/应用禁用/IP 受限（HTTP 403） |
| 50000 | 系统内部错误 |

---

## 4. 接口详情

### 4.1 创建销售订单

`POST /open/api/sales-orders`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| dealerCode | string | 是 | 经销商编码 |
| warehouseCode | string | 是 | 发货仓库编码 |
| expectedDate | string | 否 | 预计发货日期 yyyy-MM-dd |
| orderType | string | 否 | 订单类型，默认 `NORMAL` |
| dealerName | string | 否 | 经销商名称快照 |
| remark | string | 否 | 备注 |
| extra | object/string | 否 | 扩展字段，存为 JSON |
| lines | array | 是 | 明细行，至少一行 |

**lines[]**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| productCode | string | 是* | 产品编码（与 productId 二选一） |
| productId | number | 否 | 产品内部 ID（备选） |
| qty | number | 是 | 数量 |
| unitPrice | number | 是 | 单价（不含税） |
| taxRate | number | 否 | 税率，默认 0.13 |
| isGift | boolean | 否 | 是否赠品 |

**请求示例**
```json
{
  "dealerCode": "D00001",
  "warehouseCode": "WH-MAIN",
  "expectedDate": "2026-08-10",
  "remark": "外部ERP推送",
  "lines": [
    { "productCode": "PROD-000012", "qty": 5, "unitPrice": 100, "taxRate": 0.13 }
  ]
}
```

**响应 data**
```json
{ "id": 772, "code": "SO-20260802-00002", "status": "DRAFT" }
```

说明：创建后订单为 `DRAFT`（草稿）状态，由 DMS 内部后续提交、审批；审批通过后按既有规则自动生成销售出库单。金额合计由 DMS 按 `qty * unitPrice` 汇总。

---

### 4.2 创建采购订单

`POST /open/api/purchase-orders`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| supplierCode | string | 是 | 供应商编码 |
| warehouseCode | string | 是 | 收货仓库编码 |
| expectedDate | string | 否 | 预计到货日期 yyyy-MM-dd |
| orderType | string | 否 | 订单类型，默认 `NORMAL` |
| supplierName | string | 否 | 供应商名称快照 |
| remark | string | 否 | 备注 |
| extra | object/string | 否 | 扩展字段，存为 JSON |
| lines | array | 是 | 明细行，至少一行 |

**lines[]**：同销售订单（productCode/productId、qty、unitPrice、taxRate）。

**请求示例**
```json
{
  "supplierCode": "SUP-0030",
  "warehouseCode": "WH-MAIN",
  "expectedDate": "2026-08-12",
  "remark": "外部ERP推送",
  "lines": [
    { "productCode": "PROD-000016", "qty": 3, "unitPrice": 50 }
  ]
}
```

**响应 data**
```json
{ "id": 77, "code": "PO-20260802-00002", "status": "DRAFT" }
```

---

## 5. 完整调用示例（curl）

```bash
BODY='{"dealerCode":"D00001","warehouseCode":"WH-MAIN","expectedDate":"2026-08-10","lines":[{"productCode":"PROD-000012","qty":5,"unitPrice":100}]}'
TS=$(date +%s%3N)
NONCE=$(cat /proc/sys/kernel/random/uuid)
HASH=$(printf '%s' "$BODY" | sha256sum | awk '{print $1}')
SIGN_STR=$(printf 'POST\n/open/api/sales-orders\n%s\n%s\n%s' "$TS" "$NONCE" "$HASH")
SIG=$(printf '%s' "$SIGN_STR" | openssl dgst -sha256 -hmac "8c39b1f7e2a44d6b9f0a1c2d3e4f5a6b" | awk '{print $2}')

curl -X POST http://8.133.193.238:8082/open/api/sales-orders \
  -H "Content-Type: application/json" \
  -H "X-App-Key: dms-demo-app" \
  -H "X-Timestamp: $TS" \
  -H "X-Nonce: $NONCE" \
  -H "X-Signature: $SIG" \
  -d "$BODY"
```

---

## 6. 调用日志与排错

- 所有 `/open/api/**` 调用均记录在 DMS 的 **接口调用日志**（`api_call_log`，direction=IN），可在 DMS 后台「用户与权限 → 接口调用日志」查看，含请求体、响应体、状态码、耗时、appKey、系统标识。
- 签名失败时没有 requestId，请重点核对：body 字节一致性、path 是否含 query（不应含）、时间戳是否为毫秒、换行符是否为 `\n`。
- 时间戳偏差超过 5 分钟会被拒绝，请校准调用方服务器时钟（NTP）。

## 7. 安全建议

- appSecret 仅在调用方服务端保存，切勿下发到前端/客户端。
- 生产环境通过后台重置默认 appSecret，并按需配置来源 IP 白名单（`open_app.allowed_ips`）。
- 全程使用 HTTPS。
- 请妥善保存 requestId，便于双方对账与问题定位。
