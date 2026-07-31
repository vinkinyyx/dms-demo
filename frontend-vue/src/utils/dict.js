import { reactive } from 'vue'
import request from '@/utils/request'

export const LABELS = {
  id: '编号', code: '编码', name: '名称', nameCn: '中文名称', nameEn: '英文名称',
  status: '状态', createdAt: '创建时间', updatedAt: '更新时间', createdBy: '创建人',
  remark: '备注', description: '说明', version: '版本号', tenantId: '租户ID',
  username: '账号', userType: '用户类型', email: '邮箱', phone: '手机号',
  lastLoginAt: '最近登录', wechatBound: '微信绑定', mustChangePassword: '需改密',
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
  dealerName: '经销商', hospitalName: '医院', warehouseName: '仓库',
  regionName: '区域', supplierName: '供应商', productName: '产品'
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
  boolean: [{ value: true, label: '是' }, { value: false, label: '否' }]
}

const STATUS_MAP = {
  APPROVED: '已审批', ACTIVE: '启用', COMPLETED: '已完成', EFFECTIVE: '生效',
  SUBMITTED: '已提交', PENDING: '待处理', DRAFT: '草稿',
  REJECTED: '已驳回', CANCELLED: '已取消', FAILED: '失败', LOCKED: '锁定',
  INACTIVE: '停用', SUSPENDED: '挂起', EXPIRED: '已过期', PAUSED: '暂停', BLOCKED: '冻结',
  PARTIAL_RECEIVED: '部分收货', PARTIAL_SHIPPED: '部分发货', SHIPPING: '发货中', RECEIVING: '收货中', PARTIAL_CANCELLED: '部分取消', TRUE: '是', FALSE: '否'
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
  if (typeof v === 'number' && key && (key.indexOf('mount') > 0 || key.toLowerCase().indexOf('price') >= 0)) {
    return '¥ ' + Number(v).toFixed(2)
  }
  return s
}

const _dictCache = {}
const _dictLoading = {}

export function getDictOptions(type) {
  if (_dictCache[type]) return _dictCache[type]
  const arr = reactive([])
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
