import { reactive } from 'vue'
import request from '@/utils/request'

export const LABELS = {
  id: '编号', code: '编码', name: '名称', nameCn: '中文名称', nameEn: '英文名称',
  status: '状态', createdAt: '创建时间', updatedAt: '更新时间', createdBy: '创建人',
  remark: '备注', description: '说明', version: '版本号', tenantId: '租户ID',
  username: '账号', userType: '用户类型', email: '邮箱', phone: '手机号',
  lastLoginAt: '最近登录', wechatBound: '微信绑定', mustChangePassword: '需改密',
  roleId: '角色', roleName: '角色', roleIds: '角色', roleNames: '角色',
  loginFailCount: '登录失败次数', lastLoginIp: '最近登录IP',
  orgId: '组织', dealerId: '经销商', lockedUntil: '锁定至',
  spec: '规格型号', unit: '单位', currentPrice: '参考单价', price: '单价',
  taxRate: '税率', udiRequired: 'UDI追溯', warnMonths: '临期预警(月)',
  safetyQty: '安全库存', minOrderQty: '最小订购量', categoryId: '分类',
  level: '级别', legalPerson: '法人', uscNo: '统一社会信用代码', regAddress: '注册地址',
  regCapital: '注册资本', foundedAt: '成立日期', businessScope: '经营范围',
  gspStatus: 'GSP资质', gspExpire: 'GSP到期', regionId: '区域',
  contactName: '联系人', contactPhone: '联系电话', contactEmail: '联系邮箱',
  type: '类型', hospitalId: '医院', address: '地址',
  warehouseId: '仓库', fromWarehouseId: '源仓库', toWarehouseId: '目标仓库',
  orderType: '订单类型', shipAddressId: '收货地址',
  amountInclTax: '含税金额', discountAmount: '优惠金额', finalAmount: '最终金额',
  expectedDate: '期望到货', submittedAt: '提交时间', approvedAt: '审批时间',
  parentOrderId: '父订单', shipSnapshot: '收货快照',
  applicationType: '申请类型', contractCategory: '合同分类', category: '分类',
  validFrom: '生效开始', validTo: '生效结束',
  applicationId: '申请编号', contractId: '合同', pdfUrl: 'PDF地址',
  authType: '授权类型', productId: '产品', terminalId: '终端',
  qty: '数量', batchNo: '批次号', serialNo: '序列号', prodDate: '生产日期',
  expDate: '到期日期', inSource: '入库来源',
  promoType: '促销类型', priority: '优先级', exclusive: '排他',
  ruleDetail: '规则详情', dealerScope: '经销商范围', productScope: '产品范围',
  invoiceNo: '发票号', refOrderId: '关联订单', refSalesOutId: '关联销售出库',
  amount: '金额', taxAmount: '税额', issueDate: '开票日期', imageUrl: '发票图片',
  salesDate: '销售日期', arrivedAt: '到货时间',
  action: '操作', entityType: '实体类型', entityId: '实体编号', ipAddress: 'IP地址',
  atTime: '时间', userAgent: '客户端', success: '是否成功', failReason: '失败原因',
  receiverId: '接收人', channel: '通道', title: '标题', content: '内容',
  isRead: '已读', loginType: '登录方式',
  industry: '行业', typeCode: '字典类型', typeName: '类型名称', itemCode: '字典项',
  label: '标签', sortOrder: '排序', scope: '作用域', key: '键', value: '值',
  parentId: '父级', seq: '顺序', settingsCount: '参数数', ref: '关联',
  productType: '产品类型', categoryName: '产品分类',
  productLineId: '产品层次', productLineName: '产品层次',
  priceType: '价格类型', priceTypeText: '价格类型',
  priceContext: '价格用途', priceContextText: '价格用途',
  currency: '币种', inclPrice: '含税价', exclPrice: '不含税价',
  validFrom: '生效开始', validTo: '生效结束', taxRate: '税率',
  isSerialManaged: '序列号管理', productSpec: '规格', productUnit: '单位',
  productCode: '产品编码', warehouseCode: '仓库编码',
  stockStatus: '库存状态', inSource: '入库来源',
  partnerId: '经销商/供应商', partnerName: '经销商/供应商',
  dealerName: '经销商', hospitalName: '医院', warehouseName: '仓库',
  regionName: '区域', supplierName: '供应商', productName: '产品',
  orgName: '组织', roleName: '角色'
}

export function labelOf(k) {
  return LABELS[k] || k
}

export const ENUMS = {
  status: [
    { value: 'active', label: '启用' }, { value: 'inactive', label: '停用' },
    { value: 'locked', label: '锁定' }, { value: 'blocked', label: '冻结' },
    { value: 'draft', label: '草稿' }, { value: 'paused', label: '暂停' },
    { value: 'expired', label: '已过期' }, { value: 'suspended', label: '挂起' }
  ],
  userType: [{ value: 'vendor', label: '厂商' }, { value: 'dealer', label: '经销商' }],
  level: [{ value: 'T1', label: '一级' }, { value: 'T2', label: '二级' }],
  boolean: [{ value: true, label: '是' }, { value: false, label: '否' }],
  orderType: [
    { value: 'SALES', label: '销售订单' }, { value: 'STANDARD', label: '标准销售订单' },
    { value: 'RETURN', label: '销退单' }, { value: 'SALES_RETURN', label: '销售退货' },
    { value: 'PURCHASE', label: '采购订单' }, { value: 'PURCHASE_RETURN', label: '采购退货' },
    { value: 'TRANSFER', label: '调拨单' }
  ],
  productType: [
    { value: 'GOODS', label: '普通商品' }, { value: 'SERVICE', label: '服务' },
    { value: 'BOM', label: '组合品/BOM' }, { value: 'SET', label: '套装' }
  ],
  priceType: [
    { value: 'SALE', label: '销售价' }, { value: 'PURCHASE', label: '采购价' }
  ],
  priceContext: [
    { value: 'NORMAL', label: '普通价' }, { value: 'BOM_HEADER', label: 'BOM母件' },
    { value: 'BOM_CHILD', label: 'BOM子件' }, { value: 'CONTRACT', label: '合同价' }
  ],
  stockStatus: [
    { value: 'QUALIFIED', label: '合格' }, { value: 'PENDING', label: '待检' },
    { value: 'DEFECTIVE', label: '不合格' }, { value: 'QUARANTINED', label: '冻结' }
  ],
  inSource: [
    { value: 'PURCHASE', label: '采购入库' }, { value: 'SALES_RETURN', label: '销退入库' },
    { value: 'TRANSFER', label: '调拨入库' }, { value: 'INIT', label: '期初库存' },
    { value: 'ADJUST', label: '库存调整' }, { value: 'PRODUCTION', label: '生产入库' }
  ],
  currency: [
    { value: 'CNY', label: '人民币' }, { value: 'USD', label: '美元' }, { value: 'EUR', label: '欧元' }
  ],
  authType: [
    { value: 'GENERAL', label: '普通授权' }, { value: 'EXCLUSIVE', label: '独家授权' }, { value: 'TEMP', label: '临时授权' }
  ],
  gspStatus: [
    { value: 'active', label: '有效' }, { value: 'expired', label: '已过期' }, { value: 'none', label: '无' }
  ]
}

export const STATUS_MAP = {
  APPROVED: '已审批', ACTIVE: '启用', COMPLETED: '已完成', EFFECTIVE: '生效',
  SUBMITTED: '已提交', PENDING_APPROVAL: '审批中', PENDING: '待处理', DRAFT: '草稿',
  REJECTED: '已驳回', CANCELLED: '已取消', FAILED: '失败', LOCKED: '锁定',
  INACTIVE: '停用', SUSPENDED: '挂起', EXPIRED: '已过期', PAUSED: '暂停', BLOCKED: '冻结',
  PARTIAL_RECEIVED: '部分收货', PARTIAL_SHIPPED: '部分发货', SHIPPING: '发货中', RECEIVING: '收货中', PARTIAL_CANCELLED: '部分取消', TRUE: '是', FALSE: '否',
  SALES: '销售订单', PURCHASE: '采购订单', TRANSFER: '调拨', SALES_RETURN: '销售退货', PURCHASE_RETURN: '采购退货',
  STANDARD: '标准销售订单', RETURN: '销退单',
  GOODS: '普通商品', SERVICE: '服务', BOM: '组合品', SET: '套装',
  QUALIFIED: '合格', DEFECTIVE: '不合格', QUARANTINED: '冻结',
  SALE: '销售价', NORMAL: '普通', CONFIRMED: '已确认', RECEIVED: '已入库', SHIPPED: '已发货'
}

export function statusText(s) {
  if (s == null || s === '') return '-'
  const u = String(s).toUpperCase()
  return STATUS_MAP[u] || String(s)
}

export function statusTagType(s) {
  if (s == null || s === '') return 'info'
  const u = String(s).toUpperCase()
  if (['APPROVED', 'ACTIVE', 'COMPLETED', 'EFFECTIVE', 'TRUE'].includes(u)) return 'success'
  if (['SUBMITTED', 'PENDING', 'DRAFT', 'PAUSED', 'PARTIAL_RECEIVED', 'PARTIAL_SHIPPED', 'SHIPPING', 'RECEIVING', 'PARTIAL_CANCELLED'].includes(u)) return 'warning'
  if (['REJECTED', 'CANCELLED', 'FAILED', 'LOCKED', 'BLOCKED'].includes(u)) return 'danger'
  if (['INACTIVE', 'EXPIRED', 'FALSE'].includes(u)) return 'info'
  return 'primary'
}

const TIME_KEYS = ['atTime', 'createdAt', 'updatedAt', 'submittedAt', 'approvedAt', 'lastLoginAt', 'arrivedAt']

export function fmt(v, key) {
  if (v == null) return '-'
  if (typeof v === 'boolean') return v ? '是' : '否'
  if (typeof v === 'object') return JSON.stringify(v).substring(0, 80)
  const s = String(v)
  if (key && TIME_KEYS.includes(key)) return s.substring(0, 19).replace('T', ' ')
  if (key && ENUMS[key]) { const m = ENUMS[key].find((o) => String(o.value) === s); if (m) return m.label }
  if (typeof v === 'number' && key && (key.indexOf('mount') > 0 || key.toLowerCase().indexOf('price') >= 0)) {
    return '¥ ' + Number(v).toFixed(2)
  }
  return s
}

const ACTION_VERB_MAP = {
  CREATE: '创建', UPDATE: '更新', EDIT: '编辑', DELETE: '删除', REMOVE: '删除',
  SUBMIT: '提交', APPROVE: '审批通过', REJECT: '驳回', CANCEL: '取消', REVOKE: '撤销',
  ACTIVATE: '启用', DEACTIVATE: '失效', ENABLE: '启用', DISABLE: '停用', SUSPEND: '挂起',
  EXPIRE: '过期', CONFIRM: '确认', RECEIVE: '收货', SHIP: '发货', OUTBOUND: '出库', INBOUND: '入库',
  RETURN: '退货', TRANSFER: '调拨', ADJUST: '调整', IMPORT: '导入', EXPORT: '导出',
  LOGIN: '登录', LOGOUT: '登出', UPLOAD: '上传', DOWNLOAD: '下载', VIEW: '查看', PRINT: '打印',
  GENERATE: '生成', SIMULATE_SHIP: '生成出库单', COPY: '复制', CLOSE: '关闭', AUDIT: '审核'
}

const ACTION_ENTITY_MAP = {
  SO: '销售订单', PO: '采购订单', RO: '销退单', RPO: '采退单',
  PRODUCT: '产品', DEALER: '经销商', PRICE: '产品价格', AUTH: '授权',
  PROMO: '促销', CONTRACT: '合同', RECEIPT: '收货单', OUT: '出库单',
  INV: '库存', USER: '用户', ROLE: '角色', MENU: '菜单', DICT: '字典',
  WAREHOUSE: '仓库', HOSPITAL: '医院', REGION: '区域', CATEGORY: '分类',
  BOM: 'BOM', ORDER: '订单', SALES: '销售', SALES_OUT: '销售出库', SALES_RETURN: '销售退货',
  PURCHASE_RECEIPT: '采购收货', ATTACHMENT: '附件', LOG: '日志'
}

export function actionText(action) {
  if (action == null || action === '') return '-'
  if (typeof action !== 'string') return String(action)
  const trimmed = action.trim()
  if (!trimmed) return '-'
  if (/^[\u4e00-\u9fa5]/.test(trimmed)) return trimmed
  const parts = trimmed.split(/[_:：\-\s]+/).filter(Boolean)
  if (parts.length >= 2) {
    const entity = ACTION_ENTITY_MAP[parts[0].toUpperCase()]
    const verb = ACTION_VERB_MAP[parts.slice(1).join('_').toUpperCase()]
    if (entity && verb) return entity + verb
    if (entity) {
      const rest = parts.slice(1).map(p => ACTION_VERB_MAP[p.toUpperCase()] || p).join('')
      return entity + rest
    }
    const firstVerb = ACTION_VERB_MAP[parts[0].toUpperCase()]
    if (firstVerb) {
      const rest = parts.slice(1).map(p => ACTION_ENTITY_MAP[p.toUpperCase()] || ACTION_VERB_MAP[p.toUpperCase()] || p).join('')
      return rest + firstVerb
    }
  }
  const single = ACTION_VERB_MAP[trimmed.toUpperCase()]
  if (single) return single
  return trimmed
}

function translateEnumTokens(text) {
  if (!text || typeof text !== 'string') return text
  let out = text
  const enums = [ENUMS.status, ENUMS.orderType, ENUMS.productType, ENUMS.priceType, ENUMS.priceContext, ENUMS.stockStatus, ENUMS.inSource, ENUMS.currency, ENUMS.authType, ENUMS.gspStatus]
  for (const arr of enums) {
    if (!Array.isArray(arr)) continue
    for (const o of arr) {
      if (o && o.value != null && o.label) {
        const re = new RegExp('(?<![A-Za-z0-9_])' + String(o.value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '(?![A-Za-z0-9_])', 'g')
        out = out.replace(re, o.label)
      }
    }
  }
  Object.keys(STATUS_MAP).forEach((k) => {
    const re = new RegExp('(?<![A-Za-z0-9_])' + k + '(?![A-Za-z0-9_])', 'g')
    out = out.replace(re, STATUS_MAP[k])
  })
  return out
}

export function enhanceChanges(changes, context) {
  if (!changes || typeof changes !== 'string') return changes || ''
  let out = changes
  const ctx = context || {}
  const productMap = ctx.productMap
  if (/product\s*=\s*\d+/i.test(out)) {
    out = out.replace(/product\s*=\s*(\d+)/ig, (m, id) => {
      if (ctx.productName && (String(ctx.productId) === String(id) || String(ctx.id) === String(id))) {
        return `产品=${ctx.productName}${ctx.productCode ? '(' + ctx.productCode + ')' : ''}`
      }
      if (productMap instanceof Map) {
        const hit = productMap.get(String(id))
        if (hit) return `产品=${hit.label}${hit.code ? '(' + hit.code + ')' : ''}`
      }
      if (productMap && typeof productMap === 'object') {
        const hit = productMap[String(id)]
        if (hit) {
          if (typeof hit === 'string') return `产品=${hit}`
          return `产品=${hit.label || hit.name || ''}${hit.code ? '(' + hit.code + ')' : ''}`
        }
      }
      if (ctx.productMap) return `产品#${id}（不存在或已删除）`
      return m
    })
  }
  out = translateEnumTokens(out)
  return out
}

const _dictCache = {}
const _dictLoading = {}

export function getDictOptions(type) {
  if (_dictCache[type]) return _dictCache[type]
  const arr = reactive([])
  arr.__dictType = type
  _dictCache[type] = arr
  loadDict(type)
  return arr
}

export function loadDict(type) {
  if (_dictLoading[type]) return _dictLoading[type]
  const arr = _dictCache[type] || (_dictCache[type] = reactive([]))
  _dictLoading[type] = request({ url: `/api/dicts/${type}/items`, method: 'get' })
    .then((res) => {
      const list = parseResponseList(res)
      arr.splice(0, arr.length)
      list.forEach((item) => {
        arr.push({ value: item.value, label: item.label })
      })
    })
    .catch((e) => {
      console.warn('加载字典失败:', type, e && e.message)
    })
    .finally(() => { _dictLoading[type] = null })
  return _dictLoading[type]
}

function parseResponseList(res) {
  if (!res) return []
  if (Array.isArray(res)) return res
  if (Array.isArray(res.data)) return res.data
  if (res.data && Array.isArray(res.data.list)) return res.data.list
  if (res.data && Array.isArray(res.data.records)) return res.data.records
  return []
}

export function reloadDicts() {
  return Promise.all(Object.keys(_dictCache).map((type) => loadDict(type)))
}
