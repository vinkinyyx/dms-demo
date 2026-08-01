## v3.8.0 (2026-08-01) - 会话/权限、收货汇总、产品类型修复、库存移动

### 后端
- 会话：`application-test.yml` access-token-ttl 600000->28800000（8h）、refresh-token-ttl 3600000->604800000（7d）。
- 安全：`JwtFilter` 令牌解析失败返回 401/40101“登录已过期，请重新登录”；`SecurityConfig` 增加 authenticationEntryPoint(401) 与 accessDeniedHandler(403)。
- 收货汇总：`BizDocDetailController.receiptDetail` 聚合 receipt_lines 返回 totalExpected/totalReceived/totalCancelled/totalRemaining。
- 产品：`Product` 实体新增 productType/productTypeName；`ProductService` create/update 持久化 productType，列表/详情回填类型与分类名；Flyway V43 增加 products.product_type。
- 单号：`DocNoGenerator.next` 单号撞唯一键时自动顺延重试，避免共享测试库/历史数据导致 500。
- 库存移动 v3.7.9：STATUS_ADJUST 仓内状态调整 / WAREHOUSE_TRANSFER 跨仓移动，srcInventoryId 原子扣减+upsert，序列号/数量/状态校验（见下 v3.7.9）。

### 前端（frontend-vue，PC）
- `request.js`：401 自动 refresh + 请求排队重放；403 展示后端 message；统一中文文案。
- `ReceiptEdit.vue`：底部新增“收货汇总”卡片，字段缺失时前端按 poLines 兜底求和。
- 产品表单/列表：产品类型可保存并回显（modules.js 既有 productType 配置现已生效）。
- 物料选择器：分页每页 50，模糊搜索覆盖编码/名称/规格。

### 数据迁移
- 测试环境需应用 V39-V43（此前停留在 V38）；test profile 关闭 Flyway，部署时手动执行并补 flyway_schema_history。

### 测试
- 后端 `mvn test`：84 个测试全部通过（修复了历史测试路径 /auth->/api/auth、外键种子数据、DocNoGenerator 适配等）。
- 新增 ProductControllerIntegrationTest 覆盖产品类型/分类保存与回显。
- API 冒烟：34 个核心接口 200，0 个 500；前端 npm run build 通过。

## v3.7.9 (2026-08-01) - 库存移动两种模式（仓内状态调整 / 跨仓移动）

### 后端
- `stock_moves` 新增 `move_type`(STATUS_ADJUST/WAREHOUSE_TRANSFER)、`from_stock_status`、`to_stock_status`（Flyway V41，历史数据回填 WAREHOUSE_TRANSFER，move_type 扩到 VARCHAR(24)）。
- `stock_move_lines` 新增 `src_inventory_id`、`from_stock_status`、`to_stock_status`、`stock_batch_id`。
- 字典 `stock_status` 新增 `QUARANTINED(隔离)`（QUALIFIED/DEFECTIVE/PENDING 保留）。
- `POST /api/stock-moves` 重写：两种模式校验、基于 srcInventoryId 读真实库存、数量/序列号/状态一致性校验、单号 `MV-YYYYMMDD-NNNNN`（doc_no_sequences 原子自增）、按 inventory 主键原子扣减 + 按维度 upsert 增加库存、写明细/操作日志/库存流水（STATUS_ADJUST_OUT/IN、MOVE_OUT/IN，含操作人）。
- `InventoryStatusOps` 新增 `deductById`、`addByKey` 及写流水私有方法。
- 列表/详情/导出 SQL 增加 move_type、from/to_stock_status 字段。
- 单据保存即生效（COMPLETED，无草稿/审批）。

### 前端 PC（仅改 frontend-vue，不动 mobile）
- 新增 `StockMoveEdit.vue`：模式 radio 切换；源仓 picker（跨仓时显示目标仓，仓内时隐藏目标仓）；明细从库存弹窗多选（物料/批次/序列号/当前状态/在库数全部带出，不可手填）；目标状态 select；序列号产品锁定 qty=1；数量与同状态校验；提交 POST /api/stock-moves；已存在单据只读详情。
- `modules.js` stockMoves：列表清理为移动类型/源仓/目标仓/源状态/目标状态/状态/时间，新增 createPath 跳转专用编辑页，列表仅保留“查看”动作。
- `CrudView.vue` 新增 `createPath` 支持，配置后“新增”按钮路由跳转而非弹窗。
- 路由新增 `stock-move-edit/:id`（new 为新建，数字 id 为查看）。

### 验收
- 仓内状态调整 QUALIFIED→QUARANTINED 成功，库存与流水正确。
- 跨仓移动并同时改状态（QUALIFIED/QUARANTINED→PENDING）成功，目标仓按目标状态 upsert。
- 超额数量、跨仓同仓、源状态不匹配均被拒绝。
- 库存流水完整记录出/入与操作人。

## v3.7.8 (2026-07-31) - 销售出库子单模型（对齐收货入库）

### 销售出库（sales_outs）父子单重构
- 新增子单表 `sales_out_batches` / `sales_out_batch_lines`（Flyway V39），每次发货一张子单，独立保存/确认/取消，对齐 `receipt_batches` 模型
- 新增接口：
  - `POST /api/sales-outs/{id}/batches` 创建发货子单
  - `PUT  /api/sales-out-batches/{bid}` 保存子单明细
  - `POST /api/sales-out-batches/{bid}/confirm` 确认发货（扣 QUALIFIED 库存、写序列号 shipped_at、累加 shipped_qty、回写父单/源订单）
  - `POST /api/sales-out-batches/{bid}/cancel` 取消本次（仅 DRAFT，不动库存）
  - `POST /api/sales-outs/{id}/cancel-remaining` 取消剩余待发（未发数置 cancelled_qty，回写 COMPLETED）
- 关键差异（区别于收货）：子单行的批次号/序列号必须选择该仓该物料的在库合格库存（QUALIFIED），后端校验存在且数量足够，序列号产品必须选在库序列号
- 状态机：父单 DRAFT/APPROVED -> PARTIAL_SHIPPED -> COMPLETED；源销售订单 APPROVED -> SHIPPING -> COMPLETED；不再使用 PARTIAL_CANCELLED
- 取消规则：销售订单仅在出库单完全未发货时可取消（取消会级联取消出库单及其 DRAFT 子单）；一旦有发货记录订单不可修改/取消

### 前端 PC（仅改 frontend-vue，不动 mobile）
- `SalesOutEdit.vue` 整体重写为子单 UI（对齐 `ReceiptEdit.vue`）：创建发货单/保存明细/确认发货/取消本次/取消剩余；批次与序列号用下拉选择在库合格库存；新增发货汇总、已发货记录（含发货人）、关联订单税额显示
- 销售出库列表页清理为仅“打开/查看”，所有业务动作移入详情页；移除 PARTIAL_CANCELLED 筛选项
- 销售订单及采购/销退/采退的审批/驳回按钮统一去掉 `noRefresh`，操作后自动刷新，修复“审批通过后按钮不消失”

### 数据迁移
- V39 `sales_out_batches` / `sales_out_batch_lines`（幂等）

## v3.7.7 (2026-07-31) - 销售订单/销售出库对齐采购订单/收货入库

### 销售订单（orders）
- 新端点 `/api/sales-orders`（native SQL 实现，镜像 PurchaseOrderController）
- 状态机更新：`DRAFT → SUBMITTED → APPROVED → SHIPPING → COMPLETED`，新增 SHIPPING、废弃 SHIPPED
- 新增字段：`warehouse_id`（发货仓库）、`tax_amount`、`approved_by`、`completed_at`、`cancelled_at`、`extra`
- 创建必填：dealerId + warehouseId（发货仓库）；订单类型简化为 NORMAL / URGENT
- approve 后自动生成销售出库草稿（prefix `XS-*`）
- cancel 校验关联 sales_outs 均为 DRAFT/CANCELLED 且无发货，并级联取消

### 销售出库（sales_outs）
- 表结构对齐 receipts：`warehouse_id`、`remark`、`approved_at/by`、`shipped_at`、`completed_at`、`cancelled_at`、`extra`
- 明细语义对齐 receipt_lines：`expected_qty`（应发）、`shipped_qty`（累计已发）、`qty`/`quantity` 历史字段
- partialShip 按 `expectedLineId` 定位应发行（修复同产品多行累计校验缺陷），发货后回写源订单状态 APPROVED → SHIPPING / COMPLETED
- 列表返回 warehouseName；详情区分 lines（应发）/ shippedLines（执行记录）/ soLines（订单行参考）/ sourceOrder（来源订单）
- 单号前缀：红字 GIR，正常 XS（对齐采购的 GR/GRR）

### 前端 PC（本次只改 PC，不动 mobile/）
- `modules.js` orders：api → `/api/sales-orders`，新增发货仓库列、审核人列，状态项去 SHIPPED 加 SHIPPING；form 新增 warehouseId picker
- `SalesOutEdit.vue` 整体重写：布局对齐 `ReceiptEdit.vue`，新增出库单信息/关联销售订单/订单产品明细/发货明细/汇总/已发货记录/操作记录七张卡片
- `dict.js` 新增 SHIPPING、RECEIVING、PARTIAL_CANCELLED 状态文本和颜色

### 数据库
- Flyway V36：sales_order_out_align_purchase，幂等迁移（IF NOT EXISTS / COALESCE 回填历史数据）

### 启动参数
- 本地：`SPRING_FLYWAY_ENABLED=true` 启动会自动执行 V36 迁移

## v3.7.6 (2026-07-26) - 采购/收货三次调整 (6项)

### 单号规则
- 收货入库 `RK-*` → `GR-YYYYMMDD-N`, 子单 `GR-YYYYMMDD-N-M`
- 销售出库 `CK-*` / 硬编码 `SO-timestamp` → `GI-YYYYMMDD-N`
- 采购退入库 `RRK` → `GRR`, 销售退出库 `RCK` → `GIR`

### 状态机
- 收货完成 / 取消剩余 → 收货单 + PO 同步 COMPLETED (不再停留 RECEIVING)
- PO 取消 → 级联关闭对应 receipts + DRAFT 子单 (CANCELLED)

### 列表 / 详情页
- PO list 去除 `总金额` 列; 后端 JOIN users 回填 `auditUserName / auditAt`
- 收货 list 去除 `仓库ID` `源类型`
- ReceiptEdit.vue 底部新增 `操作记录` 卡片

### 数据 / 底层
- ReceiptBatchService 4 入口 res 加 `receiptId`, OperationLogAspect 优先取 receiptId 作 businessId
- 子单 `confirmed_at` = now() 作为入库时间; 页面卡片已显示

### 部署
- backend 180s + frontend 196s = 376s

## v3.7.5 (2026-07-26) - 采购/收货二次调整

### 后端
- PurchaseOrderController: submit/approve 操作日志中文化; SUBMITTED 状态禁止取消; allowedActions 移除 SUBMITTED cancel; list/detail SQL COALESCE supplier_name + JOIN suppliers 补齐显示
- ReceiptBatchService: 19 处 RuntimeException 改为 BusinessException; 修复 Java 字符串转义 bug; 6 条友好中文错误提示
- BizDocDetailController.poDetail(): 追加 allowedActions

### 前端
- CrudView.vue: 新增 rowEditable/rowDeletable 支持 editableWhen/deletableWhen; dictLabel 自动翻译 filter.options 列
- modules.js 采购订单: editableWhen=['DRAFT']; 取消 when=['DRAFT','APPROVED']

### 验证
- 端到端 PO create→submit→approve→over-qty 友好提示 = 通过
- 库存 stock_status dict U合格/Q待检/B不合格 生效
- 部署耗时 back 184s + front 189s = 373s

# 鍙樻洿鏃ュ織锛圕HANGELOG锛�

> 璁板綍 DMS 椤圭洰鏂囨。婕旇繘杩囩▼銆傛棩鏈熷�掑簭銆�

---

## v3.7.3 (2026-07-26) - 閲囪喘璁㈠崟+鏀惰揣鍏ュ簱鍏ㄦ祦绋嬩慨澶�

### 鏂板��
- 閲囪喘璁㈠崟/閿�鍞�璁㈠崟鍦� DRAFT 鐘舵�佹樉绀�"鎻愪氦瀹℃壒"鎸夐挳锛沗when` 浠� `PENDING_APPROVAL` 瀵归綈鍚庣�� `SUBMITTED`
- 閲囪喘璁㈠崟瀹℃壒閫氳繃鍚庤嚜鍔ㄥ垱寤烘敹璐у叆搴撹崏绋匡紙鍓嶇��闂�鐜�锛�
- 鏀惰揣鍏ュ簱缂栬緫椤垫柊澧�"鍏宠仈閲囪喘璁㈠崟"淇℃伅鍗★紙13 椤瑰瓧娈碉級
- 杩佺Щ V34__receipt_test_data.sql锛氭竻鐞嗗�ゅ効鏀惰揣 seed锛岄噸閫� 4 鍦烘櫙鍏宠仈 PO 鐨勬敹璐у叆搴�

### 淇�澶�
- [R5] 鏀惰揣鍏ュ簱鍒楄〃闅愯棌"鏂板缓"鎸夐挳锛坣oCreate:true锛�
- [R8] 鏀惰揣鍏ュ簱鍒楄〃闅愯棌"鍒犻櫎"鎸夐挳锛坣oDelete:true锛�
- [R3] CrudView 鏄庣粏瀛楁�� span=24 + .has-lines 鎶藉眽閾烘弧鑷�閫傚簲锛汱inesEditor 琛ㄥご min-width
- [R4] LinesEditor 蹇呭～鍒楄〃澶村姞绾㈡槦锛汣rudView 鎻愪氦鍓嶉亶鍘嗗繀濉�鏍￠獙
- [CrudView rowActions bug] 鏁扮粍褰㈠紡 statusActions 涔嬪墠浼氬洜 sa[row.status] 鍙� undefined 琚�蹇界暐
- [PurchaseOrder create] expected_date 鍙傛暟 CAST(:ed AS date)锛屼慨闀挎湡 500 閿�

### 楠岃瘉
- API E2E: tools/browser-use/e2e_v373_po_receipt.py 鍏ㄩ儴 PASS
- UI 鍐掔儫: tools/browser-use/ui_smoke_v373.py 12/12 PASS

---


## 2026-07-22 鈥� v3.5.2 + 鍙岀幆澧冮儴缃� + 鏂囨。鍚屾��

### 浜や粯鍐呭��
| 椤圭洰 | 璇存槑 |
|---|---|
| **14 椤瑰叏灞�鎬� UI/UX 鏁存敼** | 鍏ㄩ儴鎸夐渶姹傚畬鎴愶紝浠ｇ爜灞傞潰 14/14 鉁� |
| **淇�澶� 502 鐧诲綍闂�棰�** | 淇�姝� application.yml 榛樿�ゅ瘑鐮併�佸悗绔�鐜�澧冨彉閲忋�丯ginx 浠ｇ悊 |
| **淇�澶� Flyway V22 杩佺Щ** | 澶氭�′慨姝ｅ垪鍚嶅拰鍞�涓�绾︽潫锛屾渶缁堟垚鍔� |
| **閲嶇疆 admin 瀵嗙爜** | 鏁版嵁搴撲腑 V7 纭�缂栫爜鐨� BCrypt 鍝堝笇瀵瑰簲鐨勫瘑鐮佷笌鏃ュ織鎵撳嵃涓嶄竴鑷达紝閲嶇疆涓� `Sh123456` |
| **鍒涘缓娴嬭瘯鐜�澧�** | 绔�鍙� 8082/8083/5433/6380/9002/9003锛屼笌姝ｅ紡鐜�澧冨畬鍏ㄩ殧绂� |
| **鏈�鍦伴」鐩�娓呯悊** | 鍒犻櫎 50+ 涓�涓存椂娴嬭瘯鑴氭湰鍜屾棤鐢ㄦ枃浠� |
| **鏈嶅姟鍣ㄦ竻鐞�** | 娓呯悊 Docker 鏃犵敤闀滃儚鍜屽�瑰櫒锛岄噴鏀剧�佺洏绌洪棿 |

### 鍙岀幆澧冮儴缃�
- **姝ｅ紡鐜�澧冿紙鐢熶骇婕旂ず锛�**: 8.133.193.238:8081锛堝墠绔�锛�/ 8080锛堝悗绔�锛�/ 5432锛圖B锛�
- **娴嬭瘯鐜�澧冿紙寮�鍙戦獙璇侊級**: 8.133.193.238:8083锛堝墠绔�锛�/ 8082锛堝悗绔�锛�/ 5433锛圖B锛�

### 婕旂ず璐﹀彿锛堢粺涓�涓� Sh123456锛�
- 绉熸埛 `default`
- admin / director / sales1 / dealer1 / dealer2

### 椤圭洰瑙勫垯鏇存柊
- 鏂板�炪��3.1 鍙岀幆澧冪�＄悊瑙勫垯銆嶏細鎵�鏈夐渶姹傝皟鏁村彧鑳藉厛閮ㄧ讲鍒版祴璇曠幆澧冿紝鐢ㄦ埛鏄庣‘璇�"鎺ㄩ�佹�ｅ紡鐜�澧�"鎵嶈兘鏇存柊姝ｅ紡鐜�澧�

### 鏂囨。鏇存柊
- README.md锛氭洿鏂板弻鐜�澧冨湴鍧�銆佸瘑鐮佺粺涓�涓� Sh123456
- 浜ゆ帴鎬荤粨_v3.5.2.md锛氭洿鏂板瘑鐮佷负 Sh123456锛岃ˉ鍏呮祴璇曠幆澧冪��鍙ｄ俊鎭�
- .trae/project_rules.md锛氭柊澧炲弻鐜�澧冪�＄悊瑙勫垯绔犺妭
- docs/07_閮ㄧ讲鏂规��/鍓嶇��閮ㄧ讲鎶ュ憡_娴嬭瘯鐜�澧僟20260722.md锛氭祴璇曠幆澧冮儴缃茶�板綍

---

## 2026-07-18 鈥� v3.0 閲囪喘閿�鍞�鎷嗗垎 + 浣庝唬鐮�

### 鑳屾櫙
鐢ㄦ埛鍙嶉�� 5 涓�闂�棰橀渶瑕佷竴娆℃�т氦浠橈細鐘舵�侀┍鍔ㄦ寜閽�銆佷腑鏂囪�︽儏瑙嗗浘銆侀噰璐�閿�鍞�鎷嗗垎銆佸簱瀛樿仈鍔ㄣ�佷綆浠ｇ爜瀛楁�甸厤缃�銆�

### 浜や粯鍐呭��
| 妯″潡 | 璇存槑 |
|---|---|
| **鏁版嵁搴� V8** | 鏂板�� `purchase_orders`銆乣purchase_order_lines`銆乣form_configs` 琛�锛涗富琛ㄥ姞 `extra JSONB` 鍒� |
| **鍚庣�� 5 涓�鏂� Controller** | `PurchaseOrderController`銆乣OrderMetaController`銆乣FormConfigController`銆乣DictCrudController`銆乣InventorySummaryController` |
| **鍓嶇�� 3 澶勫崌绾�** | `workspace.html`锛氳彍鍗曟媶鍒� + 鐘舵�侀┍鍔ㄦ寜閽� + 涓�鏂囪�︽儏瑙嗗浘锛沗admin.html`锛氭柊澧炲瓧鍏哥淮鎶ゅ拰瀛楁�甸厤缃�椤碉紱`order-create.html`锛氶攢鍞�/閲囪喘鍙屾ā寮� |
| **娴嬭瘯** | 14/14 鍐掔儫娴嬭瘯鍏ㄩ儴閫氳繃 |
| **鏂囨。** | 鏂板�� [閲囪喘閿�鍞�鎷嗗垎+浣庝唬鐮佷氦浠樻姤鍛奯v3.0.md](docs/09_娴嬭瘯鎶ュ憡/閲囪喘閿�鍞�鎷嗗垎%2B浣庝唬鐮佷氦浠樻姤鍛奯v3.0.md) 鍜� [DMS鐜�澧冧俊鎭�.md](docs/DMS鐜�澧冧俊鎭�.md) |
| **鏁版嵁搴撳揩鐓�** | 瀵煎嚭鑷� `docs/05_鏁版嵁搴撹�捐��/schema_export/`锛�157 KB schema + 2.2 MB 鏁版嵁锛� |

### 鏈嶅姟鍣ㄧ増鏈�
- 鍚庣��闀滃儚锛歚dms-backend:2.0.2`
- 4 涓�瀹瑰櫒鍏ㄩ儴 Up 路 纾佺洏娓呯悊閲婃斁 12 GB

---

## 2026-07-18 鈥� 鍏ㄩ渶姹傝ˉ榻� v2.0锛圥0-P3 路 38 椤瑰姛鑳斤級

### 浜や粯鎵规��
| 鎵规�� | 浼樺厛绾� | 椤规暟 | 娴嬭瘯 |
|---|---|---|---|
| 鎵规�� 1 | P0 闃绘柇椤� | 6 | 15/15 鉁� |
| 鎵规�� 2 | P1 鐢ㄦ埛浣撻獙 | 10 | 10/10 鉁� |
| 鎵规�� 3 | P2 绠＄悊鑳藉姏 | 10 | 10/10 鉁� |
| 鎵规�� 4 | P3 瀹屾暣鍖� | 12 | 12/12 鉁� |

瑕嗙洊锛氬悎鍚� PDF/绛剧珷/ERP褰掓。銆乁DI杩芥函銆佹壒閲忓�煎叆瀵煎嚭銆佺患鍚堢湅鏉裤�佸緟鍔炲垪琛ㄣ�侀偖浠禩oken瀹℃壒銆佽秴鏃舵彁閱掋�佺紦瀛樼洃瑙嗐�侀泦鎴� Mock銆佷績閿�瀹℃壒銆佽繑鍒╁紩鎿庛�佸�熻揣鍗曘�丒xcel 瀵煎嚭銆佸井淇＄櫥褰� + 7 寮犵Щ鍔ㄧ�� H5 椤甸潰銆�

---

## 2026-07-18 鈥� V1 鍐崇瓥鍙樻洿锛圖-24 ~ D-41锛�

### 鑳屾櫙
鍦ㄦ�ｅ紡寮�鍙戝墠锛屼笟鍔℃柟瀵圭��浜岃疆 18 涓�寮�鍙戝墠闂�棰樿繘琛屼簡纭�璁わ紝浜у嚭 18 椤规柊鍐崇瓥 D-24 ~ D-41锛岄渶姹傚垎鏋愬笀銆佽�捐�″笀銆佹灦鏋勫笀鍚屾�ヤ慨璁㈠叏閮ㄦ牳蹇冩枃妗ｃ��

### 鍏抽敭鍐崇瓥鎽樿��

| # | 鍐崇瓥 | 缁撹�� |
|---|---|---|
| D-24 | 鍥㈤槦/宸ユ湡 | 15+ 浜� / 3-4 涓�鏈� |
| D-25 | 鍝佺墝瑙嗚�� | 浣跨敤缁勪欢搴撻粯璁や富棰橈紙Element Plus / Vant锛夛紝Logo 鐢ㄦ枃瀛楁牱寮� |
| D-26 | 浜や粯鐜�澧� | 浠呮湰鍦伴儴缃诧紙Docker Compose锛� |
| D-27 | 榛樿�よ秴绠� | 鍥哄畾 admin / Sh123456 |
| **D-28** | **淇冮攢闄嶇骇** | **V1 鍙�鍋氭弧鍑� + 璧疯�㈤噺**锛屽垹闄ゆ弧璧犱笌缁勫悎閿�鍞� |
| D-29 | UDI | 鍙�寮�鍏筹紝V1 涓嶇湡瀹炰笂鎶ョ洃绠� |
| D-30 | 鐢靛瓙绛剧珷 | Mock 濂戠害鎸� e绛惧疂 API |
| D-31 | ERP | 閫氱敤 REST锛屼笉缁戝畾鍘傚晢 |
| **D-32** | **鍒犻櫎 SSO** | V1 浠呰处鍙峰瘑鐮佺櫥褰� |
| **D-33** | **閫氱煡娓犻亾** | 绔欏唴 + 浼佸井/椋炰功 Webhook锛屽垹闄ら偖浠剁煭淇� |
| D-34 | 鎶ヨ〃 | 鍥哄畾 10 绫� + T+1 鐗╁寲瑙嗗浘 |
| D-35 | 鏉冮檺 | 鍥涘眰 RBAC + 琛岀骇锛堜笉鍋氬瓧娈电骇锛� |
| **D-36** | **H5 鐧诲綍** | 寰�淇℃壂鐮� + 棣栨�＄粦瀹� DMS 璐﹀彿 |
| D-37 | 澶氳��瑷� | 涓�鏂� + 棰勭暀 i18n |
| D-38 | 涓婚�� | 浜�鑹� + 绉熸埛鍙�鏀逛富鑹� |
| D-39 | 瀹¤�� | Excel 瀵煎嚭 + 3 骞� + MinIO 鍐峰瓨 |
| D-40 | 鎬ц兘 | PRD 榛樿�わ紙500 骞跺彂 / 50 TPS锛� |
| D-41 | 浜や粯鏂瑰紡 | 浠ｇ爜 + 鍩硅�� + 鎵嬪唽锛堜笉鍋氱伆搴﹁瘯鐐癸級 |

### 鏂囨。淇�璁㈡竻鍗�

#### 馃搫 [闇�姹傚垎鏋恄UserStory.md](d:/Workspace/TRAE/DMS/docs/02_闇�姹傚垎鏋�/闇�姹傚垎鏋恄UserStory.md)
- 鏂板�炲喅绛栬�板綍 D-24 ~ D-41 鍒般�岄浂銆佸叧閿�鍐崇瓥璁板綍銆嶈〃
- 鎵撳垹闄ょ嚎锛歎S-LOGIN-06 (SSO)銆乁S-B-Promo-03 (婊¤禒)銆乁S-B-Promo-05 (缁勫悎閿�鍞�)銆乁S-E-04 (SSO 闆嗘垚)
- 閲嶅啓锛歎S-M-01 (H5 鐧诲綍鏀逛负寰�淇℃壂鐮�)
- 鏇存柊锛歎S-E-01 閭�浠�/鐭�淇� 鈫� 浼佸井/椋炰功 Webhook
- 浼樺厛绾ф眹鎬伙細100 鈫� 94 鏉�

#### 馃搫 [楂樹繚鐪烾I璁捐�¤�存槑涔�.md](d:/Workspace/TRAE/DMS/docs/03_璁捐�″浘/楂樹繚鐪烾I璁捐�¤�存槑涔�.md)
- 鏂板�炪�孷1 鍐崇瓥鍙樻洿鎻愮ず銆嶇珷鑺傚埌鏂囨。寮�澶�
- 澹版槑 V1 閲囩敤 Element Plus / Vant 榛樿�や富棰橈紝鏈�璁捐�¤�存槑浣滀负闀挎湡鍙傝��
- 鏍囨敞淇冮攢椤甸潰绠�鍖栵紙W-13/W-14锛夈�丠5 鐧诲綍鏀归�狅紙W-24锛�

#### 馃搫 [鍔熻兘璇︾粏璁捐��.md](d:/Workspace/TRAE/DMS/docs/04_鍔熻兘璇︾粏璁捐��/鍔熻兘璇︾粏璁捐��.md)
- 鐗堟湰 V1.0 鈫� V1.1
- 鏂板�炵珷鑺傚ご銆孷1 鍐崇瓥鍙樻洿璁板綍銆嶈〃
- FDD-1 鐧诲綍锛氬垹闄� SSO锛屾柊澧� 1.5 寰�淇℃壂鐮佺櫥褰�
- FDD-2 宸ヤ綔鍙帮細娑堟伅閫氶亾鏀逛负绔欏唴+浼佸井/椋炰功
- FDD-10 淇冮攢锛歵ype 鏋氫妇缂╁噺涓� {MOQ, FULL_REDUCTION}锛屽垹闄� GIFT/BUNDLE 鐩稿叧娴佺▼
- FDD-13 瀹¤�★細Excel 瀵煎嚭 + 鍐峰綊妗� MinIO
- FDD-14 澶栭儴鎺ュ彛锛氬垹 SSO/閭�浠�/鐭�淇★紱鏂板�炲井淇� & 浼佸井/椋炰功 Webhook锛汣A 鎸� e绛惧疂濂戠害
- FDD-15 H5锛歎S-M-01 寰�淇℃壂鐮佺櫥褰曟祦绋�

#### 馃搫 [鏋舵瀯璇勫�＄邯瑕佷笌鎶�鏈�鏂规��.md](d:/Workspace/TRAE/DMS/docs/04_鍔熻兘璇︾粏璁捐��/鏋舵瀯璇勫�＄邯瑕佷笌鎶�鏈�鏂规��.md)
- 鏂板�炵珷鑺傚ご銆孷1 鍐崇瓥鍙樻洿 ADR 琛ヤ竵銆�
- 鏇存柊 ADR-02锛堜績閿�锛�/ ADR-07锛圡ock 濂戠害锛�/ ADR-08锛堥壌鏉冪Щ闄� SSO 鍔犲井淇★級/ ADR-10锛堝墠绔�涓婚�橈級
- 鏂板�� ADR-11锛氶�氱煡娓犻亾锛堢珯鍐�+浼佸井/椋炰功锛�
- 鏂板�� ADR-12锛氫氦浠樿寖鍥达紙浠呮湰鍦� Docker Compose锛�
- 閲岀▼纰戣皟鏁翠负 3.5 涓�鏈堬紙M5 涓婁簯绉诲嚭 V1锛�

#### 馃搫 [鏁版嵁搴撹�捐��_Part1.md](d:/Workspace/TRAE/DMS/docs/05_鏁版嵁搴撹�捐��/鏁版嵁搴撹�捐��_Part1.md)
- users 琛ㄦ柊澧� `wechat_openid`銆乣wechat_unionid`銆乣wechat_bound_at`銆乣sso_service_id`锛堥�勭暀锛�
- 鏂板�� unique 閮ㄥ垎绱㈠紩 `ux_users_wechat_openid`
- tenants 琛ㄦ柊澧� `attrs JSONB` 瀛楁�碉紝绾﹀畾 `primary_color` 瀛樻斁浣嶇疆
- user_login_logs.login_type 娉ㄩ噴锛歏1 浠� PASSWORD / WECHAT / REMEMBER

#### 馃搫 [鏁版嵁搴撹�捐��_Part2.md](d:/Workspace/TRAE/DMS/docs/05_鏁版嵁搴撹�捐��/鏁版嵁搴撹�捐��_Part2.md)
- promotions.promo_type 娉ㄩ噴锛歏1 浠� MOQ / FULL_REDUCTION锛孏IFT/BUNDLE 淇濈暀鏋氫妇浣嶄緵 V2 鎵╁睍
- 鏂板�� CHECK 绾︽潫 `ck_promo_type_v1`
- notifications.channel 娉ㄩ噴锛歏1 浠� INAPP / WECHAT_BOT / FEISHU_BOT
- 鏁版嵁瀛楀吀 PROMO_TYPE 鎷�娉� V1 鍚�鐢ㄨ寖鍥�

#### 馃搫 [API鎺ュ彛娓呭崟.md](d:/Workspace/TRAE/DMS/docs/06_API璁捐��/API鎺ュ彛娓呭崟.md)
- 鏂板�炴枃妗ｅご銆孷1 鍐崇瓥鍙樻洿姒傝�併��
- 鍒犻櫎 `/auth/sso/verify`
- 鏂板�炲井淇＄櫥褰� 4 鎺ュ彛锛歚/auth/wechat/qrcode`銆乣/callback`銆乣/bind`銆乣/unbind`
- 鍒犻櫎 `/integrations/mail/send`銆乣/integrations/sms/send`
- 鏂板�� `/integrations/wechat-bot/push`銆乣/integrations/feishu-bot/push`
- CA 璇存槑鎸� e绛惧疂锛汦RP 閫氱敤 REST
- 淇冮攢 API 澹版槑 promo_type 鈭� {MOQ, FULL_REDUCTION}
- 璁㈠崟鍝嶅簲鍒犻櫎 gifts 瀛楁��
- 鏂板�炲井淇″洖璋冪ず渚�

#### 馃搫 [閮ㄧ讲鏂规�坃DockerCompose涓嶴eed.md](d:/Workspace/TRAE/DMS/docs/07_閮ㄧ讲鏂规��/閮ㄧ讲鏂规�坃DockerCompose涓嶴eed.md)
- 鏂板�炪�孷1 浜や粯鑼冨洿澹版槑銆嶅紩鐢ㄥ潡
- 鎷撴墤鎬昏�堬細Mock 绉婚櫎 SMS/閭�浠讹紝鏂板�炰紒寰�/椋炰功 Webhook + 寰�淇℃壂鐮�
- docker-compose锛歛pi-gateway 鏂板�� WECHAT_APP_ID/SECRET锛沵ock-server 鎸傝浇 mocks/wechat
- Seed 鏁版嵁锛歶sers 2 涓�缁戝畾 wechat_openid锛沺romotions 鍏ㄤ负 MOQ/FULL_REDUCTION
- 绗�涔濈珷鏍囨敞涓哄弬鑰冩枃妗ｏ紙涓嶅睘 V1 浜や粯锛�
- 鏂板�炵��鍗佺珷銆孷1 鍐崇瓥鍙樻洿褰卞搷銆�

---

## 2026-07-17 鈥� 椤圭洰鍒濆�嬪寲 & 棣栬疆鍐崇瓥 D-01 ~ D-23

- PRD 鍏ㄩ噺妯″潡涓�娆℃�т笂绾�
- 涓昏�屼笟锛氬尰鐤楀櫒姊�
- 澶栭儴闆嗘垚鍏� Mock
- 绉诲姩绔�鍏ㄩ噺 H5 閫傞厤
- 澶氱�熸埛 V1 灏卞惎鐢�锛坱enant_id锛�
- 鏁版嵁搴� PostgreSQL 14+
- 閮ㄧ讲褰㈡�� Docker Compose 涓�閿�鍚�鍔�
- Seed 鍏ㄩ噺娴嬭瘯鏁版嵁

---

## 鍚庣画寰呭姙锛圡0 闇�姹傚喕缁撳墠锛�

- [ ] 瀹㈡埛绔�鎻愪緵姝ｅ紡 Logo锛堟垨纭�璁ゆ部鐢ㄦ枃瀛� Logo锛�
- [ ] 瀹㈡埛绔�鎻愪緵浼佸井/椋炰功 Webhook URL锛堢敤浜� Mock 濂戠害瀵归綈锛�
- [ ] 瀹㈡埛绔�鎻愪緵寰�淇″紑鏀惧钩鍙� AppID/AppSecret锛堝彲鍏堢敤 Mock锛�
- [ ] 鐢熶骇鐜�澧冮儴缃� checklist 寰呭啓鍏ユ搷浣滄墜鍐�
- [ ] Seed 鏁版嵁閲忔渶缁堣瘎瀹＄‘璁�

鈥斺�� END 鈥斺��


---

## 2026-07-26 · v3.7.2 · 供应商模块修复

### 交付内容

| 项目 | 说明 |
|---|---|
| **修复 Bug B08** | 供应商等级下拉空 — 新增 Flyway V33 seed `supplier_level` 字典（L1/L2/L3/L4/STRATEGIC）+ Controller 加 tenant 过滤 & DISTINCT 去重 |
| **修复 Bug B09** | 保存供应商后列表无新记录 — `OperationLogAspect` 在 `@PostMapping` 场景 businessId 为 null 导致主事务被 rollback-only 污染，实际未提交。修复：从返回值抽取 id + 独立事务 + null 时跳过 |
| **修复 Bug B10** | `suppliers` DB 缺 `level` 列（Entity 有）— V33 `ALTER TABLE ADD COLUMN IF NOT EXISTS` |
| **补齐测试数据** | V33 seed 30 条演示供应商（10 城市×10 药企品牌，覆盖 5 档等级、7:3 active:inactive） |
| **部署脚本可靠性** | `deploy-fast.ps1` 加 6 次重试 + `$ErrorActionPreference='Continue'` 局部保护，应对阿里云 SSH 短暂拒绝 |

### 端到端验证

- API: `GET /api/dicts/supplier_level/items` 返回 5 项、`POST /api/suppliers` 200 且立即可查
- AI (browser-use + DeepSeek): 登录 → 供应商管理 → 新增 dialog → 5 档下拉 → 填表保存 → 列表 total=32
- 测试环境 `dms_test.suppliers` 数据：SUP-0001~SUP-0030 + 2 条测试记录

### 相关文件

- `backend/src/main/java/com/dms/aspect/OperationLogAspect.java`
- `backend/src/main/java/com/dms/masterdata/controller/SupplierController.java`
- `backend/src/main/java/com/dms/system/controller/DictCrudController.java`
- `backend/src/main/resources/db/migration/V33__supplier_level_and_test_data.sql` (新增)
- `.trae/skills/dms-deploy/deploy-fast.ps1` (retry 硬化)
- `docs/09_测试报告/autotest/v3.7.2_supplier_fix_20260726.md` (新增)
- `.memory/layers/layer3-lessons.md` L25/L26/L27
## v3.7.7 - 2026-07-31 本地修复
### Fixed
- 修复 Flyway V37 checksum mismatch 阻塞测试环境后端启动。
- 修复销售出库部分发货时库存原生查询设置 JPA 锁模式导致的 500。
- 修复同键库存历史重复数据导致库存定位返回多行的 500；空串/NULL 批次和序列号归一匹配，并按库存数量和更新时间稳定取数。
- 确认销售订单新建保存接口 `POST /api/sales-orders` 正常，包含经销商、发货仓库、明细行、单价/税率/数量等字段。

### Verified
- `mvn package -DskipTests=true -Dmaven.test.skip=true -q` 通过。
- `npm run build`（frontend-vue）通过。
- 销售订单/销售出库 API 自动化链路 20 项检查全部通过。
- 浏览器同源保存销售订单返回新订单 ID。
