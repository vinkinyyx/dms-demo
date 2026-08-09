export const BUSINESS_TYPES = [
  { value: 'SALES_ORDER', label: '销售订单' },
  { value: 'PURCHASE_ORDER', label: '采购订单' },
  { value: 'SALES_RETURN', label: '销售退货' },
  { value: 'PURCHASE_RETURN', label: '采购退货' },
  { value: 'CONTRACT', label: '合同' },
  { value: 'AUTHORIZATION', label: '授权' }
]

export const BUSINESS_LABELS = BUSINESS_TYPES.reduce((m, t) => { m[t.value] = t.label; return m }, {})

export const TEMPLATE_STATUS = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'ENABLED', label: '已启用', type: 'success' },
  { value: 'DISABLED', label: '已停用', type: 'warning' }
]
export const TEMPLATE_STATUS_LABELS = TEMPLATE_STATUS.reduce((m, t) => { m[t.value] = t.label; return m }, {})
export const TEMPLATE_STATUS_TYPE = TEMPLATE_STATUS.reduce((m, t) => { m[t.value] = t.type; return m }, {})

export const REJECT_POLICIES = [
  { value: 'RETURN_TO_SUBMITTER', label: '退回发起人修改后重新提交' },
  { value: 'CANCEL', label: '作废单据' }
]
export const REJECT_POLICY_LABELS = REJECT_POLICIES.reduce((m, t) => { m[t.value] = t.label; return m }, {})

export const APPROVE_MODES = [
  { value: 'ANY', label: '任一人通过' },
  { value: 'ALL', label: '所有人通过' }
]
export const APPROVE_MODE_LABELS = APPROVE_MODES.reduce((m, t) => { m[t.value] = t.label; return m }, {})

export const INSTANCE_STATUS = [
  { value: 'RUNNING', label: '审批中', type: 'warning' },
  { value: 'APPROVED', label: '已通过', type: 'success' },
  { value: 'AUTO_APPROVED', label: '自动通过', type: 'success' },
  { value: 'REJECTED', label: '已作废', type: 'danger' },
  { value: 'RETURNED', label: '已退回', type: 'warning' },
  { value: 'WITHDRAWN', label: '已撤回', type: 'info' },
  { value: 'TERMINATED', label: '已终止', type: 'danger' }
]
export const INSTANCE_STATUS_LABELS = INSTANCE_STATUS.reduce((m, t) => { m[t.value] = t.label; return m }, {})
export const INSTANCE_STATUS_TYPE = INSTANCE_STATUS.reduce((m, t) => { m[t.value] = t.type; return m }, {})

export const TASK_STATUS = [
  { value: 'PENDING', label: '待处理', type: 'warning' },
  { value: 'APPROVED', label: '已同意', type: 'success' },
  { value: 'REJECTED', label: '已驳回', type: 'danger' },
  { value: 'CANCELLED', label: '已取消', type: 'info' },
  { value: 'TRANSFERRED', label: '已转办', type: 'info' },
  { value: 'TERMINATED', label: '已终止', type: 'danger' }
]
export const TASK_STATUS_LABELS = TASK_STATUS.reduce((m, t) => { m[t.value] = t.label; return m }, {})

export const ASSIGNEE_TYPE_LABELS = { USER: '账号', ROLE: '角色' }

export const CONDITION_FIELDS = [
  { value: 'finalAmount', label: '单据金额', numeric: true },
  { value: 'orderType', label: '单据类型' },
  { value: 'dealerId', label: '经销商ID', numeric: true },
  { value: 'supplierId', label: '供应商ID', numeric: true }
]

export const CONDITION_OPERATORS = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'GT', label: '大于' },
  { value: 'GTE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LTE', label: '小于等于' },
  { value: 'IN', label: '属于' }
]

export function formatTime(t) {
  if (!t) return '-'
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return t
    const pad = (n) => String(n).padStart(2, '0')
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
  } catch { return t }
}