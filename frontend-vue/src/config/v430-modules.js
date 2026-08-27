// v4.3.0 新增模块配置：产品全局折扣、客户全局折扣、代金券管理、客户注册审核
// 由 src/config/modules.js 引入并合并到 MODULE_CONFIGS。
// 折扣率表单字段使用 type: 'percent'（CrudView 通用百分比字段：录入/展示为 %，提交自动 /100）。
import { ElMessage, ElMessageBox } from 'element-plus'
import { actionResource } from '@/api/crud'

const S_ACTIVE = [{ value: 'active', label: '启用' }, { value: 'inactive', label: '停用' }]
function toggleAction(apiBase, id, path, okMsg, confirmText, reloadAfter) {
  return function (row) {
    ElMessageBox.confirm(confirmText, '提示', { type: 'warning' })
      .then(async () => {
        await actionResource(apiBase, row.id, path, 'POST')
        ElMessage.success(okMsg)
        if (reloadAfter) location.reload()
      })
      .catch(() => {})
  }
}

const productGlobalDiscounts = {
  key: 'product-global-discounts', title: '产品全局折扣', api: '/api/product-global-discounts',
  createPermission: 'product_global_discount:create', importable: false, exportable: true, batchDelete: false,
  rowActions: [
    { key: 'activate', label: '启用', type: 'success', when: ['inactive'], permissionCode: 'product_global_discount:edit' },
    { key: 'deactivate', label: '停用', type: 'warning', when: ['active'], permissionCode: 'product_global_discount:edit' }
  ],
  rowActionHandlers: {
    activate: toggleAction('/api/product-global-discounts', null, '/activate', '已启用', '确认启用该产品全局折扣？启用后将参与下单自动计价。', true),
    deactivate: toggleAction('/api/product-global-discounts', null, '/deactivate', '已停用', '确认停用该产品全局折扣？停用后不再参与计价。', true)
  },
  cols: [
    { k: 'id', l: '编号', w: 60 },
    { k: 'productCode', l: '产品编码', w: 140, filter: { type: 'text' } },
    { k: 'productName', l: '产品名称', minWidth: 180, showOverflowTooltip: true },
    { k: 'discountRateText', l: '折扣率', w: 100 },
    { k: 'validFrom', l: '生效开始', w: 120, filter: { type: 'date', range: true } },
    { k: 'validTo', l: '生效结束', w: 120, filter: { type: 'date', range: true } },
    { k: 'status', l: '状态', w: 90, tag: (r) => ({ type: r.status === 'active' ? 'success' : 'info', text: r.status === 'active' ? '启用' : '停用' }), filter: { type: 'select', options: S_ACTIVE } },
    { k: 'remark', l: '备注', minWidth: 140, showOverflowTooltip: true },
    { k: 'createdAt', l: '创建时间', w: 160 },
    { k: 'updatedAt', l: '更新时间', w: 160 }
  ],
  form: [
    { key: 'productId', label: '产品', required: true, type: 'product-picker', group: '折扣对象', readonlyOnEdit: true },
    { key: 'discountRatePercent', label: '折扣率(%)', type: 'percent', precision: 2, required: true, group: '折扣信息', placeholder: '如 90 表示 9 折（减 10%）' },
    { key: 'validFrom', label: '生效开始', type: 'date', group: '有效期' },
    { key: 'validTo', label: '生效结束', type: 'date', group: '有效期' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '状态', options: S_ACTIVE },
    { key: 'remark', label: '备注', type: 'textarea', full: true, group: '其他' }
  ],
}

const dealerGlobalDiscounts = {
  key: 'dealer-global-discounts', title: '客户全局折扣', api: '/api/dealer-global-discounts',
  createPermission: 'dealer_global_discount:create', importable: false, exportable: true, batchDelete: false,
  rowActions: [
    { key: 'activate', label: '启用', type: 'success', when: ['inactive'], permissionCode: 'dealer_global_discount:edit' },
    { key: 'deactivate', label: '停用', type: 'warning', when: ['active'], permissionCode: 'dealer_global_discount:edit' }
  ],
  rowActionHandlers: {
    activate: toggleAction('/api/dealer-global-discounts', null, '/activate', '已启用', '确认启用该客户全局折扣？启用后该客户下单自动享受折扣。', true),
    deactivate: toggleAction('/api/dealer-global-discounts', null, '/deactivate', '已停用', '确认停用该客户全局折扣？', true)
  },
  cols: [
    { k: 'id', l: '编号', w: 60 },
    { k: 'dealerName', l: '客户名称', minWidth: 180, showOverflowTooltip: true, filter: { type: 'resource', resource: 'dealers', paramKey: 'dealerId' } },
    { k: 'discountRateText', l: '折扣率', w: 100 },
    { k: 'validFrom', l: '生效开始', w: 120, filter: { type: 'date', range: true } },
    { k: 'validTo', l: '生效结束', w: 120, filter: { type: 'date', range: true } },
    { k: 'status', l: '状态', w: 90, tag: (r) => ({ type: r.status === 'active' ? 'success' : 'info', text: r.status === 'active' ? '启用' : '停用' }), filter: { type: 'select', options: S_ACTIVE } },
    { k: 'remark', l: '备注', minWidth: 140, showOverflowTooltip: true },
    { k: 'createdAt', l: '创建时间', w: 160 },
    { k: 'updatedAt', l: '更新时间', w: 160 }
  ],
  form: [
    { key: 'dealerId', label: '客户(经销商)', required: true, picker: 'dealers', group: '折扣对象', readonlyOnEdit: true },
    { key: 'discountRatePercent', label: '折扣率(%)', type: 'percent', precision: 2, required: true, group: '折扣信息', placeholder: '如 95 表示 95 折（减 5%）' },
    { key: 'validFrom', label: '生效开始', type: 'date', group: '有效期' },
    { key: 'validTo', label: '生效结束', type: 'date', group: '有效期' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '状态', options: S_ACTIVE },
    { key: 'remark', label: '备注', type: 'textarea', full: true, group: '其他' }
  ],
}

const VOUCHER_STATUS = [
  { value: 'ISSUED', label: '未使用' },
  { value: 'USED', label: '已使用' },
  { value: 'EXPIRED', label: '已过期' },
  { value: 'DISABLED', label: '已禁用' },
  { value: 'VOID', label: '已作废' }
]
const VOUCHER_SCOPE = [
  { value: 'ALL', label: '全场通用' },
  { value: 'PRODUCT', label: '指定产品' },
  { value: 'CATEGORY', label: '指定品类' }
]

const customerVouchers = {
  key: 'customer-vouchers', title: '代金券管理', api: '/api/customer-vouchers',
  noCreate: true, noEdit: true, noDelete: true, importable: false, exportable: false, batchDelete: false, maxActions: 2,
  rowActions: [
    { key: 'disable', label: '禁用', type: 'warning', when: ['ISSUED'], permissionCode: 'customer_voucher:manage' },
    { key: 'enable', label: '启用', type: 'success', when: ['DISABLED'], permissionCode: 'customer_voucher:manage' },
    { key: 'void', label: '作废', type: 'danger', when: ['ISSUED', 'DISABLED'], permissionCode: 'customer_voucher:manage' }
  ],
  rowActionHandlers: {
    disable: toggleAction('/api/customer-vouchers', null, '/disable', '已禁用', '确认禁用该代金券？禁用后客户下单将无法使用。', true),
    enable: toggleAction('/api/customer-vouchers', null, '/enable', '已启用', '确认重新启用该代金券？', true),
    void(row) {
      ElMessageBox.confirm('作废后该代金券将永久不可使用，确认作废？', '危险操作', { type: 'error', confirmButtonText: '确认作废', cancelButtonText: '取消' })
        .then(async () => { await actionResource('/api/customer-vouchers', row.id, '/void', 'POST'); ElMessage.success('已作废'); location.reload() })
        .catch(() => {})
    }
  },
  cols: [
    { k: 'id', l: '编号', w: 60 },
    { k: 'code', l: '券码', w: 180, filter: { type: 'text' } },
    { k: 'name', l: '券名称', minWidth: 160, showOverflowTooltip: true, filter: { type: 'text' } },
    { k: 'dealerName', l: '客户', w: 160, filter: { type: 'resource', resource: 'dealers', paramKey: 'dealerId' } },
    { k: 'faceValue', l: '面值', w: 100 },
    { k: 'minSpend', l: '最低消费', w: 100 },
    { k: 'scopeType', l: '适用范围', w: 110, filter: { type: 'select', options: VOUCHER_SCOPE } },
    { k: 'validFrom', l: '生效开始', w: 160 },
    { k: 'validTo', l: '生效结束', w: 160 },
    { k: 'status', l: '状态', w: 100,
      tag: (r) => {
        const map = { ISSUED: 'success', USED: 'info', EXPIRED: 'warning', DISABLED: 'warning', VOID: 'danger' }
        const opt = VOUCHER_STATUS.find((o) => o.value === r.status)
        return { type: map[r.status] || 'info', text: opt ? opt.label : r.status }
      },
      filter: { type: 'select', options: VOUCHER_STATUS } },
    { k: 'batchNo', l: '发放批次', w: 160 },
    { k: 'createdAt', l: '创建时间', w: 160 }
  ]
}

const REGISTRATION_STATUS = [
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' }
]

const customerRegistrations = {
  key: 'customer-registrations', title: '客户注册审核', api: '/api/customer-registrations',
  noCreate: true, noEdit: true, noDelete: true, importable: false, exportable: false, batchDelete: false,
  maxActions: 2, detailable: false,
  rowActions: [
    { key: 'approve', label: '审核通过', type: 'success', when: ['PENDING'], permissionCode: 'customer_registration:approve' },
    { key: 'reject', label: '驳回', type: 'danger', when: ['PENDING'], permissionCode: 'customer_registration:approve' },
  ],
  rowActionHandlers: {
    approve(row) {
      ElMessageBox.confirm('审核通过后将自动创建客户账号与客户主数据，确认通过该注册申请？', '审核通过', { type: 'success', confirmButtonText: '确认通过', cancelButtonText: '取消' })
        .then(async () => { await actionResource('/api/customer-registrations', row.id, '/approve', 'POST'); ElMessage.success('已通过，账号与客户主数据已创建'); location.reload() })
        .catch(() => {})
    },
    reject(row) {
      ElMessageBox.prompt('请输入驳回原因（客户修改后可重新提交）', '驳回注册申请', {
        type: 'warning', confirmButtonText: '确认驳回', cancelButtonText: '取消', inputType: 'textarea',
        inputPlaceholder: '如：资质附件不清晰，请重新上传营业执照',
        inputValidator: (v) => (v && v.trim() ? true : '驳回原因不能为空')
      }).then(async ({ value }) => {
        await actionResource('/api/customer-registrations', row.id, '/reject', 'POST', { rejectReason: value.trim() })
        ElMessage.success('已驳回')
        location.reload()
      }).catch(() => {})
    }
  },
  cols: [
    { k: 'id', l: '编号', w: 60 },
    { k: 'companyName', l: '公司名称', minWidth: 200, showOverflowTooltip: true, filter: { type: 'text' } },
    { k: 'registerName', l: '申请人', w: 100 },
    { k: 'phone', l: '申请电话', w: 130, filter: { type: 'text' } },
    { k: 'contactName', l: '联系人', w: 100 },
    { k: 'contactPhone', l: '联系人电话', w: 130 },
    { k: 'legalPerson', l: '法人', w: 100 },
    { k: 'uscNo', l: '统一社会信用代码', w: 180, showOverflowTooltip: true },
    { k: 'regAddress', l: '注册地址', minWidth: 180, showOverflowTooltip: true },
    { k: 'status', l: '状态', w: 100,
      tag: (r) => {
        const map = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
        const opt = REGISTRATION_STATUS.find((o) => o.value === r.status)
        return { type: map[r.status] || 'info', text: opt ? opt.label : r.status }
      },
      filter: { type: 'select', options: REGISTRATION_STATUS } },
    { k: 'rejectReason', l: '驳回原因', minWidth: 160, showOverflowTooltip: true },
    { k: 'createdAt', l: '申请时间', w: 160 },
    { k: 'reviewedAt', l: '审核时间', w: 160 }
  ]
}

export const V430_MODULES = {
  'product-global-discounts': productGlobalDiscounts,
  'dealer-global-discounts': dealerGlobalDiscounts,
  'customer-vouchers': customerVouchers,
  'customer-registrations': customerRegistrations
}

export const V430_CONSTANTS = { VOUCHER_STATUS, VOUCHER_SCOPE, REGISTRATION_STATUS }
