<template>
  <div class="list-page-layout">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <template v-for="f in visibleFilters" :key="f.filterKey">
          <el-input
            v-if="f.componentType === 'input'"
            v-model="filter[f.filterKey]"
            :placeholder="f.placeholder || f.label"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          />
          <el-select
            v-else-if="f.componentType === 'select'"
            v-model="filter[f.filterKey]"
            :placeholder="f.placeholder || f.label"
            :multiple="!!f.multiple"
            clearable
            style="width: 220px"
            @change="onSearch"
          >
            <el-option
              v-for="opt in filterOptions(f)"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-date-picker
            v-else-if="f.componentType === 'date'"
            v-model="filter[f.filterKey]"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="f.placeholder || f.label"
            clearable
            style="width: 180px"
            @change="onSearch"
          />
          <el-date-picker
            v-else-if="f.componentType === 'date-range'"
            v-model="filter[f.filterKey]"
            type="daterange"
            :start-placeholder="f.label + '开始'"
            :end-placeholder="f.label + '结束'"
            value-format="YYYY-MM-DD"
            style="width: 380px"
            @change="onSearch"
          />
        </template>

        <el-button
          v-for="b in fixedToolbar"
          :key="b.buttonKey"
          :type="b.buttonType || 'default'"
          @click="b.buttonKey === 'search' ? onSearch() : onReset()"
        >{{ b.label }}</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button
          v-for="b in optionalToolbar"
          :key="b.buttonKey"
          :type="b.buttonType || 'default'"
          v-has="b.permissionCode"
          @click="onToolbarAction(b)"
        >{{ b.label }}</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe size="small" :max-height="maxHeight">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column
          v-for="col in tableColumns"
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          show-overflow-tooltip
        >
          <template v-if="col.tag" #default="{ row }">
            <el-tag :type="row[col.prop] === col.tag.success ? 'success' : 'info'">{{ row[col.prop] }}</el-tag>
          </template>
          <template v-else-if="col.render" #default="{ row }">
            {{ col.render(row) }}
          </template>
          <template v-else #default="{ row }">{{ formatAuto(row[col.prop], col.prop) }}</template>
        </el-table-column>

        <el-table-column v-if="visibleRowActions.length" label="操作" fixed="right" :width="rowColumnWidth">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                v-for="(b, idx) in visibleFlatRowActions"
                :key="b.buttonKey + '_' + idx"
                size="small"
                :type="b.buttonType || 'primary'"
                @click="onRowAction(b, row)"
              >{{ b.label }}</el-button>
              <el-dropdown v-if="visibleOverflowRowActions.length" trigger="click" @command="(command) => onRowAction(command, row)">
                <el-button size="small">更多<i class="el-icon-arrow-down el-icon--right" /></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="b in visibleOverflowRowActions"
                      :key="b.buttonKey"
                      :command="b"
                      :divided="b.rowButtonPosition === 'danger'"
                    >
                      <span :class="{ 'text-danger': b.buttonType === 'danger' || b.rowButtonPosition === 'danger' }">{{ b.label }}</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无数据" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        class="pager"
        @current-change="load"
        @size-change="load"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPageLayout } from '@/api/admin'
import { loadDict, getDictOptions } from '@/utils/dict'
import { useUserStore } from '@/store/user'
import { getPermissions } from '@/utils/auth'
import { formatAuto } from '@/utils/format'

const userStore = useUserStore()

const props = defineProps({
  pageKey: { type: String, required: true },
  tableColumns: { type: Array, required: true },
  fetchData: { type: Function, required: true },
  rowActions: { type: Object, default: () => ({}) },
  toolbarActions: { type: Object, default: () => ({}) },
  initialFilter: { type: Object, default: () => ({}) },
  maxHeight: { type: [Number, String], default: 560 }
})

const layout = ref({ filters: [], columns: [], toolbar: [], rowButtons: [] })
const filter = reactive({ ...props.initialFilter })
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const visibleFilters = computed(() => (layout.value.filters || [])
  .filter(b => b.visible !== false && b.status !== 'inactive')
  .slice()
  .sort((a, b) => (a.sortOrder || 100) - (b.sortOrder || 100)))

const visibleToolbar = computed(() => (layout.value.toolbar || [])
  .filter(b => b.visible !== false && b.status !== 'inactive')
  .slice()
  .sort((a, b) => (a.sortOrder || 100) - (b.sortOrder || 100)))

const fixedToolbar = computed(() => visibleToolbar.value.filter(b => ['search', 'reset'].includes(b.buttonKey)))
const optionalToolbar = computed(() => visibleToolbar.value.filter(b => !['search', 'reset'].includes(b.buttonKey)))

const localRowActionMap = computed(() => {
  const defaults = {
    'dealer-profile': { buttonKey: 'view', label: '\u67e5\u770b', buttonType: 'primary', permissionCode: 'dealer:view', sortOrder: 10 }
  }
  return defaults[props.pageKey] || null
})

function hasPermission(code) {
  if (!code) return true
  const all = new Set()
  if (Array.isArray(userStore.permissions)) userStore.permissions.forEach(p => all.add(p))
  const user = userStore.user || {}
  if (Array.isArray(user.permissions)) user.permissions.forEach(p => all.add(p))
  if (Array.isArray(user.roles)) user.roles.forEach(p => all.add(p))
  getPermissions().forEach(p => all.add(p))
  return all.has(String(code))
}

const visibleRowActions = computed(() => {
  const actions = (layout.value.rowButtons || [])
    .filter(b => b.visible !== false && b.status !== 'inactive')
    .slice()
    .sort((a, b) => (a.sortOrder || 100) - (b.sortOrder || 100))
  if (props.rowActions && Object.keys(props.rowActions).length) {
    for (const [key, handler] of Object.entries(props.rowActions)) {
      if (!handler || actions.some(b => b.buttonKey === key)) continue
      actions.push({ buttonKey: key, label: key === 'view' ? '\u67e5\u770b' : key, buttonType: key === 'create' ? 'primary' : 'default', permissionCode: null, sortOrder: 100 + actions.length })
    }
  }
  if (localRowActionMap.value && !actions.some(b => b.buttonKey === localRowActionMap.value.buttonKey)) {
    actions.unshift(localRowActionMap.value)
  }
  return actions.filter(b => hasPermission(b.permissionCode))
})

const visibleFlatRowActions = computed(() => visibleRowActions.value.slice(0, 1))
const visibleOverflowRowActions = computed(() => visibleRowActions.value.slice(1))

const rowColumnWidth = computed(() => visibleRowActions.value.length > 1 ? 170 : 96)

function filterOptions(f) {
  if (Array.isArray(f.options) && f.options.length) return f.options
  if (f.dictType) return getDictOptions(f.dictType)
  return []
}

async function loadLayout() {
  try {
    const res = await getPageLayout(props.pageKey)
    layout.value = res.data || { filters: [], columns: [], toolbar: [], rowButtons: [] }
  } catch (e) {
    layout.value = { filters: [], columns: [], toolbar: [], rowButtons: [] }
  }
  const dictTypes = new Set()
  for (const f of layout.value.filters || []) {
    if (f.dictType) dictTypes.add(f.dictType)
  }
  await Promise.all([...dictTypes].map(async (t) => {
    try { await loadDict(t) } catch { /* ignore */ }
  }))
}

async function load() {
  loading.value = true
  try {
    const res = await props.fetchData({ page: page.value, size: size.value, ...cleanFilter(filter) })
    const data = res?.data || res || {}
    rows.value = data.records || data.rows || data.list || data || []
    total.value = data.total || rows.value.length
  } catch (e) {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function cleanFilter(source) {
  const result = {}
  for (const [key, value] of Object.entries(source)) {
    if (value === '' || value === null || value === undefined) continue
    result[key] = value
  }
  return result
}

function onSearch() {
  page.value = 1
  load()
}

function onReset() {
  for (const key of Object.keys(filter)) delete filter[key]
  Object.assign(filter, props.initialFilter)
  page.value = 1
  load()
}

function onToolbarAction(b) {
  const fn = props.toolbarActions[b.buttonKey]
  if (fn) return fn(b)
  ElMessage.info('未配置按钮 ' + b.buttonKey + ' 的回调')
}

async function onRowAction(b, row) {
  if (b.confirmRequired) {
    try {
      await ElMessageBox.confirm(`确认执行「${b.label}」操作？`, '提示', { type: 'warning' })
    } catch {
      return
    }
  }
  const fn = props.rowActions[b.buttonKey]
  if (fn) return await fn(row, b)
  ElMessage.info('未配置按钮 ' + b.buttonKey + ' 的回调')
}

onMounted(async () => {
  await loadLayout()
  load()
})

watch(() => props.pageKey, async () => {
  await loadLayout()
  load()
})

defineExpose({ load, reload: load })
</script>

<style scoped>
.list-page-layout { display: flex; flex-direction: column; gap: 12px; }
.filter-card :deep(.el-card__body) { padding: 16px; }
.filter-bar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.table-card :deep(.el-card__body) { padding: 16px; }
.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.row-actions { display: flex; gap: 6px; justify-content: center; align-items: center; }
.pager { margin-top: 12px; justify-content: flex-end; display: flex; }
.text-danger { color: var(--el-color-danger); }
</style>
