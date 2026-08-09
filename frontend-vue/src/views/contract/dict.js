export const CATEGORY_OPTIONS = [
  { value: 'SALES', label: '销售合同' },
  { value: 'DISTRIBUTION', label: '经销合同' },
  { value: 'AUTHORIZATION', label: '授权合同' },
  { value: 'SERVICE', label: '服务合同' },
  { value: 'SUPPLY', label: '供货合同' },
  { value: 'PROMOTION', label: '促销合同' }
]

export const APP_TYPE_OPTIONS = [
  { value: 'NEW', label: '新签' },
  { value: 'MODIFY', label: '变更' },
  { value: 'RENEW', label: '续签' },
  { value: 'TERMINATE', label: '终止' }
]

export const STATUS_OPTIONS = [
  { value: 'draft', label: '草稿', tag: 'info' },
  { value: 'pending', label: '审批中', tag: 'warning' },
  { value: 'effective', label: '已生效', tag: 'success' },
  { value: 'rejected', label: '已驳回', tag: 'danger' },
  { value: 'terminated', label: '已终止', tag: 'info' },
  { value: 'expired', label: '已到期', tag: 'info' }
]

export const TEMPLATE_STATUS_OPTIONS = [
  { value: 'draft', label: '草稿', tag: 'info' },
  { value: 'published', label: '已发布', tag: 'success' },
  { value: 'disabled', label: '已停用', tag: 'info' }
]

export const FIELD_TYPE_OPTIONS = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'amount', label: '金额' },
  { value: 'date', label: '日期' },
  { value: 'select', label: '下拉选择' },
  { value: 'checkbox', label: '复选框' }
]

export function categoryLabel(v) {
  return (CATEGORY_OPTIONS.find((i) => i.value === v) || {}).label || v || '-'
}
export function appTypeLabel(v) {
  return (APP_TYPE_OPTIONS.find((i) => i.value === v) || {}).label || v || '-'
}
export function statusMeta(v) {
  return STATUS_OPTIONS.find((i) => i.value === v) || { label: v || '-', tag: 'info' }
}
export function templateStatusMeta(v) {
  return TEMPLATE_STATUS_OPTIONS.find((i) => i.value === v) || { label: v || '-', tag: 'info' }
}