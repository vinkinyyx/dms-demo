from pathlib import Path
p=Path('D:/Workspace/TRAE/DMS/frontend-vue/src/components/CrudView.vue')
s=p.read_text(encoding='utf-8')
old="""const layoutFilterFields = computed(() => (pageLayout.filters || [])
  .filter((f) => f.visible !== false && f.status !== 'inactive')
  .slice()
  .sort((a, b) => (a.sortOrder || 100) - (b.sortOrder || 100)))

const showLegacyKeyword = computed(() => !activeLayoutFilterKeys.value.has('keyword'))
const legacyFilterFields = computed(() => (props.config.cols || [])
  .filter((c) => c.filter && !activeLayoutFilterKeys.value.has(c.k)))"""
new="""const layoutFilterFields = computed(() => (pageLayout.filters || [])
  .filter((f) => f.visible !== false && f.status !== 'inactive')
  .slice()
  .sort((a, b) => (a.sortOrder || 100) - (b.sortOrder || 100)))

const hasLayoutFilters = computed(() => layoutFilterFields.value.length > 0)
const showLegacyKeyword = computed(() => !hasLayoutFilters.value)
const legacyFilterFields = computed(() => hasLayoutFilters.value
  ? []
  : (props.config.cols || []).filter((c) => c.filter))"""
if old not in s:
    print('block not found')
else:
    p.write_text(s.replace(old,new), encoding='utf-8', newline='\n')
    print('patched')
