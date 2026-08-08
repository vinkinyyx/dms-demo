from pathlib import Path
p=Path('frontend-vue/src/components/CrudView.vue')
s=p.read_text(encoding='utf-8')
s=s.replace("import { ref, reactive, computed, watch, nextTick } from 'vue'", "import { ref, reactive, computed, watch, nextTick } from 'vue'")
s=s.replace("import { statusText, statusTagType, fmt, labelOf, reloadDicts } from '@/utils/dict'", "import { statusText, statusTagType, fmt, labelOf, reloadDicts, loadDict, getDictOptions } from '@/utils/dict'")
s=s.replace("const { layout: pageLayout, load: loadPageLayout, hasPermission: layoutHasPermission, visibleToolbar, visibleRowButtons } = usePageLayout(props.config && props.config.key)", "const { layout: pageLayout, load: loadPageLayout, visibleToolbar, visibleRowButtons } = usePageLayout(props.config && props.config.key)")
s=s.replace("const colFilters = reactive({})", "const colFilters = reactive({})\nconst layoutFilters = reactive({})\nconst layoutRangeFilters = reactive({})")
s=s.replace("const filterFields = computed(() => (props.config.cols || []).filter((c) => c.filter))", """const activeLayoutFilterKeys = computed(() => new Set((pageLayout.filters || [])
  .filter((f) => f.visible !== false && f.status !== 'inactive')
  .map((f) => f.filterKey)))

const layoutFilterFields = computed(() => (pageLayout.filters || [])
  .filter((f) => f.visible !== false && f.status !== 'inactive')
  .slice()
  .sort((a, b) => (a.sortOrder || 100) - (b.sortOrder || 100)))

const showLegacyKeyword = computed(() => !activeLayoutFilterKeys.value.has('keyword'))
const legacyFilterFields = computed(() => (props.config.cols || [])
  .filter((c) => c.filter && !activeLayoutFilterKeys.value.has(c.k)))""")
old_watch = """watch(() => props.config, async () => {
  page.value = 1
  keyword.value = ''
  Object.keys(colFilters).forEach((k) => delete colFilters[k])
  if (props.config && props.config.key) {
    invalidatePageLayoutCache(props.config.key)
    await loadPageLayout(true)
  }
  fetchData()
}, { immediate: true })"""
new_watch = """watch(() => props.config, async () => {
  page.value = 1
  keyword.value = ''
  Object.keys(colFilters).forEach((k) => delete colFilters[k])
  Object.keys(layoutFilters).forEach((k) => delete layoutFilters[k])
  Object.keys(layoutRangeFilters).forEach((k) => delete layoutRangeFilters[k])
  if (props.config && props.config.key) {
    invalidatePageLayoutCache(props.config.key)
    await loadPageLayout(true)
  }
  await Promise.all(layoutFilterFields.value.map((f) => f.dictType ? loadDict(f.dictType).catch(() => {}) : null))
  fetchData()
}, { immediate: true })"""
s=s.replace(old_watch,new_watch)
s=s.replace("""const mustButtonKeys = ['search', 'reset']
const extraToolbarButtons = computed(() => {
  const all = visibleToolbar()
  return all.filter((b) => !mustButtonKeys.includes(b.buttonKey))
})
const hasResetButton = computed(() => visibleToolbar().some((b) => b.buttonKey === 'reset'))

function onResetForm() {
  keyword.value = ''
  Object.keys(colFilters).forEach((k) => delete colFilters[k])
  page.value = 1
  fetchData()
}""", """const mustButtonKeys = ['search', 'reset']
const extraToolbarButtons = computed(() => visibleToolbar()
  .filter((b) => !mustButtonKeys.includes(b.buttonKey)))

function onResetForm() {
  keyword.value = ''
  Object.keys(colFilters).forEach((k) => delete colFilters[k])
  Object.keys(layoutFilters).forEach((k) => delete layoutFilters[k])
  Object.keys(layoutRangeFilters).forEach((k) => delete layoutRangeFilters[k])
  page.value = 1
  fetchData()
}""")
s=s.replace("""const maxFlatRowButtons = computed(() => {
  // 1 个不折叠；2-4 平铺；>4 折叠到 1 个"详情/最高频"
  const n = visibleRowButtons().length
  if (n <= 1) return 1
  if (n <= 4) return n
  return 1
})""", """const maxFlatRowButtons = computed(() => visibleRowButtons().length <= 1 ? 1 : 1)""")
s=s.replace("""function rowActionVisible(b, row) {
  // row_status_only 限制
  if (b.statusIn && Array.isArray(b.statusIn) && b.statusIn.length && !b.statusIn.includes(row && row.status)) return false
  if (b.statusNotIn && Array.isArray(b.statusNotIn) && b.statusNotIn.length && !b.statusNotIn.includes(row && row.status)) return false
  return true
}""", """function rowActionVisible(b, row) {
  if (b.statusIn && Array.isArray(b.statusIn) && b.statusIn.length && !b.statusIn.includes(row && row.status)) return false
  if (b.statusNotIn && Array.isArray(b.statusNotIn) && b.statusNotIn.length && b.statusNotIn.includes(row && row.status)) return false
  const legacy = legacyActionForButton(b, row)
  if (legacy && legacy.when && !legacy.when.includes(row && row.status)) return false
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
}""")
s=s.replace("""async function onRowButtonClick(b, row) {
  // 内置按钮：view / edit / delete
  if (b.buttonKey === 'view') return openDetail(row)
  if (b.buttonKey === 'edit') return openForm(row)
  if (b.buttonKey === 'delete') return onDelete(row)
  // 自定义：config.rowActionHandlers?.[b.buttonKey]?.(row, b)
  if (props.config.rowActionHandlers && typeof props.config.rowActionHandlers[b.buttonKey] === 'function') {
    return props.config.rowActionHandlers[b.buttonKey](row, b)
  }
  // 兜底：走 doAction（老路径）
  ElMessage.info('未配置按钮 ' + b.buttonKey + ' 的回调，请在 config.toolbarHandlers 中提供')
}""", """async function onRowButtonClick(b, row) {
  if (b.buttonKey === 'view') return openDetail(row)
  if (b.buttonKey === 'edit') return openForm(row)
  if (b.buttonKey === 'delete') return onDelete(row)
  if (props.config.rowActionHandlers && typeof props.config.rowActionHandlers[b.buttonKey] === 'function') {
    return props.config.rowActionHandlers[b.buttonKey](row, b)
  }
  const legacy = legacyActionForButton(b, row)
  if (legacy) return doAction(row, legacy)
  ElMessage.info('未配置按钮 ' + b.buttonKey + ' 的回调')
}""")
s=s.replace("""const operationWidth = computed(() => {
  // 至少 88；按钮数 n：1=88, 2-3=160, 4=240；>4 时折叠 200
  const n = visibleRowButtons().length
  if (n === 0) return 88
  if (n === 1) return 88
  if (n <= 3) return 160
  if (n === 4) return 240
  return 200
})""", """const operationWidth = computed(() => {
  const n = visibleRowButtons().length
  if (n <= 1) return 96
  return 180
})""")
s=s.replace("""    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    Object.keys(colFilters).forEach((k) => { if (colFilters[k] !== '' && colFilters[k] != null) params[k] = colFilters[k] })""", """    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    Object.keys(layoutFilters).forEach((k) => {
      const v = layoutFilters[k]
      if (v !== '' && v != null) params[k] = v
    })
    Object.keys(layoutRangeFilters).forEach((k) => {
      const v = layoutRangeFilters[k]
      if (Array.isArray(v) && v.length === 2) {
        params[k + 'From'] = v[0]
        params[k + 'To'] = v[1]
      }
    })
    Object.keys(colFilters).forEach((k) => { if (colFilters[k] !== '' && colFilters[k] != null) params[k] = colFilters[k] })""")
p.write_text(s, encoding='utf-8', newline='\n')
