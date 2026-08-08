from pathlib import Path
p=Path('frontend-vue/src/components/CrudView.vue')
s=p.read_text(encoding='utf-8')
start=s.index('function rowActionVisible')
end=s.index('\nfunction onToolbarButtonClick', start)
replacement=r'''function rowActionVisible(b, row) {
  if (b.statusIn && Array.isArray(b.statusIn) && b.statusIn.length && !b.statusIn.includes(row && row.status)) return false
  if (b.statusNotIn && Array.isArray(b.statusNotIn) && b.statusNotIn.length && b.statusNotIn.includes(row && row.status)) return false
  const legacy = legacyActionForButton(b, row)
  if (legacy && Array.isArray(legacy.when) && !legacy.when.includes(row && row.status)) return false
  return true
}

function legacyActionForButton(b, row) {
  const cfg = props.config || {}
  if (b.buttonKey === 'edit') return { key: 'edit' }
  if (b.buttonKey === 'delete') return { key: 'delete' }
  if (b.buttonKey === 'view') {
    if (cfg.detailPath) return { key: 'view', isRoute: true, path: cfg.detailPath }
    if (cfg.statusActions) {
      const list = Array.isArray(cfg.statusActions) ? cfg.statusActions : (cfg.statusActions[row && row.status] || [])
      const open = list.find((a) => normalizeActionKey(a) === 'open')
      if (open) return open
    }
    if (Array.isArray(cfg.actions)) {
      const open = cfg.actions.find((a) => normalizeActionKey(a) === 'open')
      if (open) return open
    }
    return { key: 'view' }
  }
  if (cfg.statusActions) {
    const list = Array.isArray(cfg.statusActions) ? cfg.statusActions : (cfg.statusActions[row && row.status] || [])
    const exact = list.find((a) => normalizeActionKey(a) === b.buttonKey)
    if (exact) return exact
  }
  if (Array.isArray(cfg.actions)) {
    const exact = cfg.actions.find((a) => normalizeActionKey(a) === b.buttonKey)
    if (exact) return exact
  }
  const standardActions = {
    submit: { method: 'POST', path: '/submit', type: 'warning', confirm: '确认提交？' },
    approve: { method: 'POST', path: '/approve', type: 'success', confirm: '确认审批通过？' },
    reject: { method: 'POST', path: '/reject', type: 'danger', confirm: '确认驳回？' },
    cancel: { method: 'POST', path: '/cancel', type: 'warning', confirm: '确认取消？' },
    confirm: { method: 'POST', path: '/confirm', type: 'success', confirm: '确认执行？' },
    execute: { method: 'POST', path: '/execute', type: 'success', confirm: '确认执行？' }
  }
  return standardActions[b.buttonKey] ? { key: b.buttonKey, ...standardActions[b.buttonKey] } : null
}

function normalizeActionKey(a) {
  return a.key || a.buttonKey || actionKeyFromPath(a.path)
}

function actionKeyFromPath(path) {
  if (!path) return ''
  return String(path).replace(/^\//, '').replace(/[^a-zA-Z0-9]+(.)/g, (_, c) => c.toUpperCase())
}

function filterOptions(f) {
  if (Array.isArray(f.options) && f.options.length) return f.options
  if (f.dictType) return getDictOptions(f.dictType)
  const col = (props.config.cols || []).find((c) => c.k === f.filterKey)
  return col && col.filter ? col.filter.options || [] : []
}
'''
s=s[:start]+replacement+s[end:]
p.write_text(s, encoding='utf-8', newline='\n')
