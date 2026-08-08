from pathlib import Path
p=Path('frontend-vue/src/components/CrudView.vue')
s=p.read_text(encoding='utf-8')
s=s.replace("""function rowActionVisible(b, row) {
  // row_status_only 限制
  if (b.statusIn && Array.isArray(b.statusIn) && b.statusIn.length && !b.statusIn.includes(row && row.status)) return false
  if (b.statusNotIn && Array.isArray(b.statusNotIn) && b.statusNotIn.length && !b.statusNotIn.includes(row && row.status)) return false
  return true
}
""", """function rowActionVisible(b, row) {
  if (b.statusIn && Array.isArray(b.statusIn) && b.statusIn.length && !b.statusIn.includes(row && row.status)) return false
  if (b.statusNotIn && Array.isArray(b.statusNotIn) && b.statusNotIn.length && !b.statusNotIn.includes(row && row.status)) return false
  const legacy = legacyActionForButton(b, row)
  if (legacy && Array.isArray(legacy.when) && !legacy.when.includes(row && row.status)) return false
  return true
}

function legacyActionForButton(b, row) {
  const cfg = props.config || {}
  if (b.buttonKey === 'edit') return { key: 'edit' }
  if (b.buttonKey === 'delete') return { key: 'delete' }
  if (cfg.statusActions) {
    const list = Array.isArray(cfg.statusActions)
      ? cfg.statusActions
      : (cfg.statusActions[row && row.status] || [])
    return list.find((a) => normalizeActionKey(a) === b.buttonKey) || null
  }
  if (Array.isArray(cfg.actions)) {
    return cfg.actions.find((a) => normalizeActionKey(a) === b.buttonKey) || null
  }
  return null
}

function normalizeActionKey(a) {
  return a.key || a.buttonKey || actionKeyFromPath(a.path)
}

function actionKeyFromPath(path) {
  if (!path) return ''
  return String(path).replace(/^\\//, '').replace(/[^a-zA-Z0-9]+(.)/g, (_, c) => c.toUpperCase())
}

function filterOptions(f) {
  if (Array.isArray(f.options) && f.options.length) return f.options
  if (f.dictType) return getDictOptions(f.dictType)
  const col = (props.config.cols || []).find((c) => c.k === f.filterKey)
  return col && col.filter ? col.filter.options || [] : []
}
""")
s=s.replace("""  // 兜底：走 doAction（老路径）
  ElMessage.info('未配置按钮 ' + b.buttonKey + ' 的回调')
}""", """  const legacy = legacyActionForButton(b, row)
  if (legacy) return doAction(row, legacy)
  ElMessage.info('未配置按钮 ' + b.buttonKey + ' 的回调')
}""")
p.write_text(s, encoding='utf-8', newline='\n')
