<template>
  <div class="crud-container">
    <div class="panel-title">{{ config.label || config.title || '数据管理' }}</div>
    <div class="page-toolbar">
      <template v-for="f in layoutFilterFields" :key="f.filterKey">
        <el-input
          v-if="f.componentType === 'input'"
          v-model="layoutFilters[f.filterKey]"
          :placeholder="f.placeholder || f.label"
          clearable
          style="width: 220px"
          @keyup.enter="reload"
        />
        <el-select
          v-else-if="f.componentType === 'select'"
          v-model="layoutFilters[f.filterKey]"
          :placeholder="f.placeholder || f.label"
          :multiple="!!f.multiple"
          clearable
          style="width: 160px"
          @change="reload"
        >
          <el-option v-for="o in filterOptions(f)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-date-picker
          v-else-if="f.componentType === 'date'"
          v-model="layoutFilters[f.filterKey]"
          type="date"
          value-format="YYYY-MM-DD"
          :placeholder="f.placeholder || f.label"
          clearable
          style="width: 160px"
          @change="reload"
        />
        <el-date-picker
          v-else-if="f.componentType === 'date-range'"
          v-model="layoutRangeFilters[f.filterKey]"
          type="daterange"
          value-format="YYYY-MM-DD"
          :start-placeholder="f.label + '开始'"
          :end-placeholder="f.label + '结束'"
          clearable
          style="width: 260px"
          @change="reload"
        />
      </template>

      <el-input v-if="showLegacyKeyword" v-model="keyword" placeholder="关键词搜索" clearable style="width: 220px" @keyup.enter="reload">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <template v-for="f in legacyFilterFields" :key="f.k">
        <el-select v-if="f.filter.type === 'select'" v-model="colFilters[f.k]" :placeholder="f.l" clearable
          style="width: 150px" @change="reload">
          <el-option v-for="o in f.filter.options" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-date-picker v-else-if="f.filter.type === 'date'" v-model="colFilters[f.k]" type="date" value-format="YYYY-MM-DD"
          :placeholder="f.l" clearable style="width: 150px" @change="reload" />
        <el-input v-else-if="f.filter.type === 'text'" v-model="colFilters[f.k]" :placeholder="f.l" clearable
          style="width: 180px" @keyup.enter="reload" />
        <el-input-number v-else-if="f.filter.type === 'number'" v-model="colFilters[f.k]" :placeholder="f.l" controls-position="right"
          :min="0" style="width: 130px" @change="reload" />
      </template>

      <el-button type="primary" @click="reload"><el-icon><Search /></el-icon>查询</el-button>
      <el-button @click="onResetForm"><el-icon><RefreshLeft /></el-icon>重置</el-button>
      <div class="spacer" />
      <slot name="extra-actions" />
      <el-button v-if="canBatchDelete" type="danger" plain :disabled="!selectedRows.length" @click="onBatchDelete">批量删除{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}</el-button>
      <template v-for="b in extraToolbarButtons" :key="b.buttonKey">
        <el-button
          :type="b.buttonType || 'default'"
          v-has="b.permissionCode"
          @click="onToolbarButtonClick(b)"
        >{{ b.label }}</el-button>
      </template>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe size="small" @sort-change="onSortChange" :default-sort="{ prop: 'updatedAt', order: 'descending' }">
      <el-table-column v-for="c in config.cols" :key="c.k" :prop="c.k" :label="c.l" :width="c.w"
        :sortable="c.sortable === false ? false : 'custom'" show-overflow-tooltip>
        <template #header>
          <span>{{ c.l }}</span>
          <el-icon v-if="c.filter" class="filter-icon" @click.stop="openFilter(c, $event)">
            <Filter :color="colFilters[c.k] != null && colFilters[c.k] !== '' ? '#1677ff' : '#c0c4cc'" />
          </el-icon>
        </template>
        <template #default="{ row }">
          <el-tag v-if="c.isStatus || c.k === 'status'" :type="statusTagType(row[c.k])" size="small">{{ statusText(row[c.k]) }}</el-tag>
          <el-link v-else-if="c.link && row[c.link.valueKey] != null" type="primary"
            @click="goLink(c.link, row)">{{ linkLabel(c, row) }}</el-link>
          <el-link v-else-if="c.k === 'code' && config.detailable" type="primary" @click="openDetail(row)">{{ row[c.k] }}</el-link>
          <span v-else>{{ displayCell(c, row[c.k]) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" :width="operationWidth">
        <template #default="{ row }">
          <div class="row-actions">
            <template v-for="(b, idx) in visibleFlatRowButtons(row)" :key="b.buttonKey + '_' + idx">
              <el-button
                v-if="idx < maxFlatRowButtons"
                size="small"
                :type="b.buttonType || 'default'"
                v-has="b.permissionCode"
                @click="onRowButtonClick(b, row)"
              >{{ b.label }}</el-button>
            </template>
            <el-dropdown v-if="overflowRowButtons(row).length" size="small" trigger="click" @command="(cmd) => onRowButtonClick(cmd, row)">
              <el-button size="small">更多<i class="el-icon--right"><el-icon><ArrowDown /></el-icon></i></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <template v-for="b in overflowRowButtons(row)" :key="b.buttonKey">
                    <el-dropdown-item
                      v-has="b.permissionCode"
                      :command="b"
                      :divided="b.rowButtonPosition === 'danger'"
                    >
                      <span :class="{ 'text-danger': b.buttonType === 'danger' || b.rowButtonPosition === 'danger' }">
                        {{ b.label }}
                      </span>
                    </el-dropdown-item>
                  </template>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 漏斗过滤 popover（单例 + virtualRef，绑定到当前激活的表头漏斗图标） -->
    <el-popover
      ref="filterPopoverRef"
      :virtual-ref="filterTriggerRef"
      virtual-triggering
      :show-arrow="false"
      placement="bottom-start"
      :width="220"
      :visible="filterPopoverVisible"
      :teleported="false"
      @update:visible="(v) => { if (!v) filterPopoverVisible = false }"
      popper-class="crud-filter-popover"
    >
      <div v-if="currentFilterCol">
        <div style="margin-bottom: 8px; color: var(--dms-text-2); font-weight: 600;">{{ currentFilterCol.l }} 过滤</div>
        <el-select v-if="currentFilterCol.filter?.type === 'select'" v-model="colFilters[currentFilterCol.k]"
          style="width:100%" clearable size="small" @click.stop>
          <el-option v-for="o in currentFilterCol.filter.options" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-date-picker v-else-if="currentFilterCol.filter?.type === 'date'" v-model="colFilters[currentFilterCol.k]"
          type="date" value-format="YYYY-MM-DD" style="width:100%" size="small" />
        <el-input v-else v-model="colFilters[currentFilterCol.k]" placeholder="输入过滤值" clearable size="small" />
        <div style="margin-top: 10px; text-align: right;">
          <el-button size="small" link @click="clearCurrentFilter">清除</el-button>
          <el-button size="small" type="primary" @click="applyFilter">应用</el-button>
        </div>
      </div>
    </el-popover>

    <div class="pager">
      <el-pagination background layout="total, prev, pager, next, sizes, jumper" :total="total"
        v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50, 100]"
        @current-change="fetchData" @size-change="onSizeChange" />
    </div>

    <!-- 表单抽屉 -->
    <el-drawer v-model="formVisible" direction="rtl" size="100%" :title="editing ? '编辑' : '新增'" :modal="true" :close-on-click-modal="false" destroy-on-close>
      <div class="crud-form-container" :class="{ 'has-lines': hasLines }">
        <el-form :model="formData" label-width="150px">
          <template v-for="grp in groupedFields" :key="grp.name">
            <el-divider v-if="grp.name" content-position="left">{{ grp.name }}</el-divider>
            <el-row :gutter="16">
              <el-col v-for="f in grp.items" :key="f.key" :span="isLinesField(f) || isFull(f) ? 24 : 12">
                <el-form-item v-if="f.type !== 'lines'" :label="f.label" :required="f.required">
                  <ResourcePicker v-if="f.picker || f.type === 'product-picker'" v-model="formData[f.key]"
                    :resource="pickerResource(f)" :placeholder="f.label" :display-value="displayMap[f.key]" />
                  <el-input v-else-if="!f.type || f.type === 'text' || f.type === 'email'" v-model="formData[f.key]" :placeholder="f.placeholder" :readonly="f.readonly" />
                  <el-input v-else-if="f.type === 'password'" v-model="formData[f.key]" type="password" show-password :placeholder="f.placeholder" />
                  <el-input v-else-if="f.type === 'textarea'" v-model="formData[f.key]" type="textarea" :rows="2" :placeholder="f.placeholder" />
                  <el-input-number v-else-if="f.type === 'number'" v-model="formData[f.key]" :controls="false" style="width:100%" />
                  <el-date-picker v-else-if="f.type === 'date'" v-model="formData[f.key]" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                  <el-select v-else-if="f.type === 'select'" v-model="formData[f.key]" style="width:100%" clearable :multiple="!!f.multiple" :collapse-tags="!!f.multiple" :collapse-tags-tooltip="!!f.multiple" :teleported="false" popper-class="crud-select-popper">
                    <el-option v-for="o in selectOptions(f)" :key="o.value !== undefined ? o.value : o.label" :label="o.label" :value="o.value" />
                  </el-select>
                  <el-switch v-else-if="f.type === 'boolean'" v-model="formData[f.key]" :disabled="f.readonly" />
                  <AttachmentUploader v-else-if="f.type === 'attachment'" v-model="formData[f.key]" />
                  <MultiSelectPicker v-else-if="f.type === 'multiselect'" v-model="formData[f.key]" :resource="f.picker && f.picker.resource" />
                  <el-input v-else v-model="formData[f.key]" :placeholder="f.placeholder" />
                </el-form-item>
                <LinesEditor v-else v-model="formData[f.key]" :field="f" />
              </el-col>
            </el-row>
          </template>
        </el-form>
      </div>
      <template #footer>
        <div style="padding: 0 20px 20px;">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :direction="'rtl'" :size="'100%'" title="详情" :modal="true" destroy-on-close>
      <div style="padding: 20px; max-width: 1200px; margin: 0 auto;">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item v-for="k in detailKeys" :key="k" :label="labelOf(k)">
            <el-tag v-if="k === 'status'" :type="statusTagType(detailData[k])" size="small">{{ statusText(detailData[k]) }}</el-tag>
            <span v-else>{{ fmt(detailData[k], k) }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <template v-if="detailLines.length">
          <el-divider content-position="left">明细</el-divider>
          <el-table :data="detailLines" border size="small">
            <el-table-column v-for="k in detailLineKeys" :key="k" :prop="k" :label="labelOf(k)">
              <template #default="{ row }">{{ fmt(row[k], k) }}</template>
            </el-table-column>
          </el-table>
        </template>
        <el-divider content-position="left">操作记录</el-divider>
        <el-table :data="detailLogs" border style="width:100%;margin-bottom:10px" v-loading="operationLoading">
          <el-table-column prop="username" label="操作人" width="120" />
          <el-table-column prop="action" label="操作" width="100" />
          <el-table-column prop="changes" label="变更内容" />
          <el-table-column prop="atTime" label="操作时间" width="160" />
        </el-table>
        <el-empty v-if="!detailLogs.length" description="暂无操作记录" />
      </div>
    </el-drawer>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importVisible" title="导入数据" width="500px">
      <el-upload ref="uploadRef" :action="importUrl" :headers="uploadHeaders"
        :on-success="onImportSuccess" :on-error="onImportError" :show-file-list="false" accept=".xlsx,.xls" :auto-upload="false"
        :before-upload="beforeImport">
        <el-button type="primary">选择Excel文件</el-button>
      </el-upload>
      <p style="margin-top:12px;color:var(--dms-text-4)">支持 .xlsx 和 .xls 格式，请按模板填写数据。导入按“编码”判断：编码已存在则更新该行（留空的列保留原值），不存在则新增。</p>
      <el-button v-if="canDownloadTemplate" size="small" type="text" :loading="tplDownloading" @click="downloadTemplate">下载导入模板</el-button>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Download, Filter, Search, Plus, ArrowDown, RefreshLeft } from '@element-plus/icons-vue'
import ResourcePicker from '@/components/ResourcePicker.vue'
import MultiSelectPicker from '@/components/MultiSelectPicker.vue'
import LinesEditor from '@/components/LinesEditor.vue'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import { listResource, createResource, updateResource, deleteResource, getDetail, actionResource, getOperationLogs, httpGet } from '@/api/crud'
import { statusText, statusTagType, fmt, labelOf, reloadDicts, loadDict, getDictOptions } from '@/utils/dict'
import { getToken } from '@/utils/auth'
import { formatAuto } from '@/utils/format'
import { usePageLayout, invalidatePageLayoutCache } from '@/composables/usePageLayout'

function dictLabel(col, v) {
  if (v == null || v === '') return '-'
  const opts = col && col.filter && col.filter.type === 'select' ? col.filter.options : null
  if (opts && opts.length) {
    const hit = opts.find(o => String(o.value) === String(v))
    if (hit && hit.label) return hit.label
  }
  return fmt(v, col.k)
}

const props = defineProps({ config: { type: Object, required: true } })
const router = useRouter()

// === D13: 拉取 pageKey 布局配置（platform_button_configs 合并：平台默认 + 租户覆盖） ===
const { layout: pageLayout, load: loadPageLayout, visibleToolbar, visibleRowButtons } = usePageLayout(props.config && props.config.key)
watch(() => props.config && props.config.key, (k) => { invalidatePageLayoutCache(k); loadPageLayout(true) }, { immediate: false })

const rows = ref([])
const loading = ref(false)
const keyword = ref('')
const colFilters = reactive({})
const layoutFilters = reactive({})
const layoutRangeFilters = reactive({})
const page = ref(1)
const size = ref(20)
const total = ref(0)
const sortField = ref('updatedAt')
const sortOrder = ref('desc')

const formVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const formData = reactive({})
const displayMap = reactive({})
const editingId = ref(null)

const detailVisible = ref(false)
const detailData = ref({})
const detailLines = ref([])
const detailLogs = ref([])
const operationLoading = ref(false)

const filterPopoverVisible = ref(false)
const currentFilterCol = ref(null)
const filterTriggerRef = ref(null)
const filterPopoverRef = ref(null)

const importVisible = ref(false)
const uploadRef = ref(null)
const importing = ref(false)
const tplDownloading = ref(false)
const importUrl = computed(() => props.config.api + '/batch-import')
const uploadHeaders = computed(() => ({ 'Authorization': 'Bearer ' + (getToken() || '') }))

const searchable = computed(() => props.config.searchable !== false)
const canCreate = computed(() => !props.config.readonly && !props.config.noCreate)
const canEdit = computed(() => !props.config.readonly)
const canDelete = computed(() => !props.config.readonly && !props.config.noDelete)
const canBatchDelete = computed(() => canDelete.value && props.config.batchDelete === true)
const canImport = computed(() => !props.config.readonly && props.config.importable === true)
const canExport = computed(() => props.config.exportable === true)
const canDownloadTemplate = computed(() => canImport.value && props.config.templateable !== false)
const hasCodeColumn = computed(() => (props.config.cols || []).some((c) => c.k === 'code'))
const showDetailButton = computed(() => props.config.showDetailButton === true || !props.config.detailable || !hasCodeColumn.value)


const activeLayoutFilterKeys = computed(() => new Set((pageLayout.filters || [])
  .filter((f) => f.visible !== false && f.status !== 'inactive')
  .map((f) => f.filterKey)))

const layoutFilterFields = computed(() => (pageLayout.filters || [])
  .filter((f) => f.visible !== false && f.status !== 'inactive')
  .slice()
  .sort((a, b) => (a.sortOrder || 100) - (b.sortOrder || 100)))

const hasLayoutFilters = computed(() => layoutFilterFields.value.length > 0)
const showLegacyKeyword = computed(() => !hasLayoutFilters.value)
const legacyFilterFields = computed(() => hasLayoutFilters.value
  ? []
  : (props.config.cols || []).filter((c) => c.filter))

const groupedFields = computed(() => {
  const fields = props.config.form || []
  const map = {}
  const order = []
  fields.forEach((f) => {
    const g = f.group || ''
    if (!map[g]) { map[g] = []; order.push(g) }
    map[g].push(f)
  })
  return order.map((g) => ({ name: g, items: map[g] }))
})

const detailKeys = computed(() => {
  const data = detailData.value || {}
  const keys = Object.keys(data).filter((k) => {
    const v = data[k]
    return typeof v !== 'object' || v == null
  })
  const idNamePairs = [['categoryId', 'categoryName'], ['dealerId', 'dealerName'], ['hospitalId', 'hospitalName'], ['warehouseId', 'warehouseName'], ['supplierId', 'supplierName'], ['regionId', 'regionName'], ['productId', 'productName'], ['roleId', 'roleName']]
  idNamePairs.forEach(([idKey, nameKey]) => {
    if (keys.includes(idKey) && keys.includes(nameKey) && data[nameKey]) {
      const idx = keys.indexOf(idKey)
      if (idx > -1) keys.splice(idx, 1)
    }
  })
  return keys
})
const detailLineKeys = computed(() => (detailLines.value.length ? Object.keys(detailLines.value[0]).filter((k) => k !== 'id' && typeof detailLines.value[0][k] !== 'object') : []))

watch(() => props.config, async () => {
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
}, { immediate: true })

const PICKER_NAME_MAP = {
  orgId: 'orgName', dealerId: 'dealerName', hospitalId: 'hospitalName',
  warehouseId: 'warehouseName', supplierId: 'supplierName', regionId: 'regionName',
  productId: 'productName', categoryId: 'categoryName', contractId: 'contractName'
}

function isFull(f) { return f.type === 'textarea' || f.full }
function isLinesField(f) { return f.type === 'lines' }
const hasLines = computed(() => (props.config.form || []).some((f) => f.type === 'lines'))
function pickerResource(f) { return f.type === 'product-picker' ? 'products' : (f.picker && f.picker.resource) || f.picker }

// 字段级下拉选项：支持静态 f.options，或 f.optionsUrl 异步加载（带缓存）
const asyncOptionsCache = {}
function selectOptions(f) {
  if (Array.isArray(f.options)) return f.options
  if (f.optionsUrl) {
    if (!asyncOptionsCache[f.key]) {
      asyncOptionsCache[f.key] = ref([])
      loadAsyncOptions(f)
    }
    return asyncOptionsCache[f.key].value
  }
  return []
}
async function loadAsyncOptions(f) {
  try {
    const res = await httpGet(f.optionsUrl, f.optionsParams || {})
    const list = Array.isArray(res.data) ? res.data : (res.data && (res.data.list || res.data.records)) || []
    asyncOptionsCache[f.key].value = list.map((it) => ({
      value: it[f.optionValue || 'id'],
      label: it[f.optionLabel || 'name'] != null ? String(it[f.optionLabel || 'name']) : String(it[f.optionValue || 'id'])
    }))
  } catch (e) {
    asyncOptionsCache[f.key].value = []
  }
}
function stripEmoji(s) { return String(s || '').replace(/[\u{1F000}-\u{1FFFF}\u2600-\u27BF✅❌✓]/gu, '').trim() }


// === D13: 工具栏按钮由 layout 驱动 ===
// 必含按钮：search/reset；其余为业务按钮（按 sortOrder 排）
const mustButtonKeys = ['search', 'reset']
const extraToolbarButtons = computed(() => visibleToolbar()
  .filter((b) => !mustButtonKeys.includes(b.buttonKey))
  // 业务模块显式标记 noCreate 时，隐藏后端布局下发的“新增/新建”按钮，避免打开空白表单
  .filter((b) => !(props.config.noCreate && b.buttonKey === 'create')))

function onResetForm() {
  keyword.value = ''
  Object.keys(colFilters).forEach((k) => delete colFilters[k])
  Object.keys(layoutFilters).forEach((k) => delete layoutFilters[k])
  Object.keys(layoutRangeFilters).forEach((k) => delete layoutRangeFilters[k])
  page.value = 1
  fetchData()
}

// === D13: 行内按钮由 layout.rowButtons 驱动，支持折叠 ===
const maxFlatRowButtons = computed(() => visibleRowButtons().length <= 1 ? 1 : 1)
function visibleFlatRowButtons(row) {
  return visibleRowButtons().filter((b) => rowActionVisible(b, row))
}
function overflowRowButtons(row) {
  const flat = visibleFlatRowButtons(row)
  if (flat.length <= maxFlatRowButtons.value) return []
  return flat.slice(maxFlatRowButtons.value)
}
function rowActionVisible(b, row) {
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

function onToolbarButtonClick(b) {
  // 内置：search/reset 由布局代码直接处理；其余按 buttonKey 分发
  if (b.buttonKey === 'import') return handleImport()
  if (b.buttonKey === 'export') return handleExport()
  if (b.buttonKey === 'create') return onCreate()
  if (b.buttonKey === 'search') return reload()
  if (b.buttonKey === 'reset') return onResetForm()
  if (b.buttonKey === 'batch_delete') {
    // 业务模块可在 config.batchDeleteHandler 提供
    if (typeof props.config.batchDeleteHandler === 'function') {
      return props.config.batchDeleteHandler(selectedRows.value)
    }
    ElMessage.warning('请配置 config.batchDeleteHandler')
    return
  }
  // auto: config.toolbarHandlers?.['buttonKey']?.(b)
  if (props.config.toolbarHandlers && typeof props.config.toolbarHandlers[b.buttonKey] === 'function') {
    return props.config.toolbarHandlers[b.buttonKey](b)
  }
  ElMessage.info('未配置按钮 ' + b.buttonKey + ' 的回调，请在 config.toolbarHandlers 中提供')
}

async function onRowButtonClick(b, row) {
  if (props.config.rowActionHandlers && typeof props.config.rowActionHandlers[b.buttonKey] === 'function') {
    return props.config.rowActionHandlers[b.buttonKey](row, b)
  }
  if (b.buttonKey === 'view') {
    const legacyView = legacyActionForButton(b, row)
    if (legacyView && legacyView.key !== 'view') return doAction(row, legacyView)
    return openDetail(row)
  }
  if (b.buttonKey === 'edit') return openForm(row)
  if (b.buttonKey === 'delete') return onDelete(row)
  if (b.buttonKey === 'reset_pwd' || b.buttonKey === 'reset-password' || b.buttonKey === 'resetPassword') return onResetPassword(row)
  if (b.buttonKey === 'unlock') return onUnlock(row)
  const legacy = legacyActionForButton(b, row)
  if (legacy) return doAction(row, legacy)
  ElMessage.info('未配置按钮 ' + b.buttonKey + ' 的回调')
}

async function onResetPassword(row) {
  try {
    const { value } = await ElMessageBox.prompt('请为「' + (row.name || row.username) + '」设置新密码（至少 8 位）', '重置密码', {
      inputType: 'password',
      inputPlaceholder: '请输入新密码',
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
      inputValidator: (v) => {
        if (!v || v.length < 8) return '密码至少 8 位'
        if (v.length > 64) return '密码不超过 64 位'
        return true
      }
    })
    await actionResource(props.config.api, row.id, '/reset-password', 'post', { newPassword: value })
    ElMessage.success('密码已重置')
    fetchData()
  } catch (e) { /* 用户取消 */ }
}

async function onUnlock(row) {
  try {
    await ElMessageBox.confirm('确认解锁账号「' + (row.name || row.username) + '」？', '解锁', { type: 'info' })
    await actionResource(props.config.api, row.id, '/unlock', 'post')
    ElMessage.success('账号已解锁')
    fetchData()
  } catch (e) { /* 用户取消 */ }
}

const selectedRows = ref([])

// === D13: 动态 operationWidth 适配折叠 ===
const operationWidth = computed(() => {
  const n = visibleRowButtons().length
  if (n <= 1) return 96
  return 180
})
async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value, limit: size.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
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
    Object.keys(colFilters).forEach((k) => { if (colFilters[k] !== '' && colFilters[k] != null) params[k] = colFilters[k] })
    if (sortField.value) params.sort = sortField.value + ',' + (sortOrder.value === 'ascending' ? 'asc' : 'desc')
    if (props.config.extraParams) Object.assign(params, props.config.extraParams)
    const res = await listResource(props.config.api, params)
    const data = res.data
    if (Array.isArray(data)) { rows.value = data; total.value = data.length }
    else if (data && Array.isArray(data.list)) { rows.value = data.list; total.value = data.total ?? data.list.length }
    else if (data && Array.isArray(data.records)) { rows.value = data.records; total.value = data.total ?? data.records.length }
    else { rows.value = []; total.value = 0 }
  } finally {
    loading.value = false
  }
}

function reload() { page.value = 1; fetchData() }
function onSizeChange() { page.value = 1; fetchData() }
function onSortChange({ prop, order }) { sortField.value = prop; sortOrder.value = order; fetchData() }

function linkLabel(c, row) { return c.linkLabelKey && row[c.linkLabelKey] ? row[c.linkLabelKey] : row[c.k] }
function displayCell(c, value) {
  const label = dictLabel(c, value)
  return label === value ? formatAuto(value, c.k) : label
}
function goLink(link, row) { router.push('/m/' + link.menu) }


function rowEditable(row) {
  const cfg = props.config
  if (!cfg) return true
  if (cfg.editableWhen && Array.isArray(cfg.editableWhen)) {
    return cfg.editableWhen.includes(row && row.status)
  }
  return true
}
function rowDeletable(row) {
  const cfg = props.config
  if (!cfg) return true
  if (cfg.deletableWhen && Array.isArray(cfg.deletableWhen)) {
    return cfg.deletableWhen.includes(row && row.status)
  }
  return true
}

function rowActions(row) {
  const sa = props.config.statusActions
  if (sa) {
    if (Array.isArray(sa)) {
      return sa.filter((a) => !a.when || a.when.includes(row.status))
    }
    return sa[row.status] || []
  }
  if (props.config.actions) return props.config.actions
  return []
}

function doAction(row, a) {
  if (a.isRoute) {
    router.push(a.path + '/' + row.id)
    return
  }
  ElMessageBox.confirm(a.confirm || ('确认执行「' + stripEmoji(a.label) + '」？'), '提示', { type: a.type === 'danger' ? 'warning' : 'info' })
    .then(async () => {
      await actionResource(props.config.api, row.id, a.path, a.method || 'post', a.body)
      ElMessage.success('操作成功')
      if (!a.noRefresh) {
        fetchData()
      }
    })
    .catch(() => {})
}

function onCreate() {
  if (props.config.createPath) { router.push(props.config.createPath); return }
  openForm(null)
}
function openForm(row) {
  editing.value = !!row
  editingId.value = row ? row.id : null
  Object.keys(formData).forEach((k) => delete formData[k])
  Object.keys(displayMap).forEach((k) => delete displayMap[k])
  ;(props.config.form || []).forEach((f) => {
    if (row) {
      formData[f.key] = row[f.key]
      if (f.picker || f.type === 'product-picker') {
        const nameKey = PICKER_NAME_MAP[f.key] || (f.key.replace(/Id$/, '') + 'Name')
        if (row[nameKey]) displayMap[f.key] = row[nameKey]
      }
    } else if (f.value !== undefined) formData[f.key] = f.value
    else if (f.type === 'lines') formData[f.key] = []
    else if (f.type === 'boolean') formData[f.key] = false
    else if (f.type === 'attachment') formData[f.key] = null
    else formData[f.key] = ''
  })
  if (row && props.config.detailable && props.config.api) {
    getDetail(props.config.api, row.id).then((res) => {
      const d = res.data || {}
      ;(props.config.form || []).forEach((f) => {
        if (d[f.key] !== undefined && d[f.key] !== null) formData[f.key] = d[f.key]
        if ((f.picker || f.type === 'product-picker') && d[f.key] != null) {
          const nameKey = PICKER_NAME_MAP[f.key] || (f.key.replace(/Id$/, '') + 'Name')
          if (d[nameKey]) displayMap[f.key] = d[nameKey]
        }
      })
    }).catch(() => {})
  }
  reloadDicts()
  formVisible.value = true
}

async function onSubmit() {
  // 前置校验：顶层 required 字段
  const fields = props.config.form || []
  for (const f of fields) {
    if (f.type === 'lines') {
      if (f.required && (!Array.isArray(formData[f.key]) || formData[f.key].length === 0)) {
        ElMessage.warning('请至少添加一行“' + f.label + '”')
        return
      }
      const rows = Array.isArray(formData[f.key]) ? formData[f.key] : []
      const requiredCols = (f.cols || []).filter((c) => c.required)
      for (let i = 0; i < rows.length; i++) {
        for (const c of requiredCols) {
          const v = rows[i][c.k]
          if (v === '' || v == null || (typeof v === 'number' && isNaN(v))) {
            ElMessage.warning('第 ' + (i + 1) + ' 行“' + c.l + '”不能为空')
            return
          }
        }
      }
    } else if (f.required && !f.readonly) {
      const v = formData[f.key]
      if (v === '' || v == null) {
        ElMessage.warning('请填写“' + f.label + '”')
        return
      }
      if (f.type === 'password' && typeof v === 'string' && v.length < 8) {
        ElMessage.warning('“' + f.label + '”至少 8 位')
        return
      }
    } else if (f.type === 'password' && formData[f.key] && !f.readonly) {
      if (String(formData[f.key]).length < 8) {
        ElMessage.warning('“' + f.label + '”至少 8 位')
        return
      }
    }
  }
  saving.value = true
  try {
    const payload = {}
    Object.keys(formData).forEach((k) => {
      const v = formData[k]
      if (v === '' || v == null) return
      if (k === 'attachment' && typeof v === 'object') {
        payload.attachmentFileId = v.fileId
        payload.attachmentName = v.originalName
        payload.attachmentUrl = v.url
        return
      }
      payload[k] = v
    })
    delete payload.attachment
    if (editing.value) { await updateResource(props.config.api, editingId.value, payload); ElMessage.success('更新成功') }
    else {
      const createApi = props.config.apiCreate || props.config.api
      await createResource(createApi, payload)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    fetchData()
  } catch (e) { /* 拦截器已提示 */ } finally { saving.value = false }
}

function onDelete(row) {
  ElMessageBox.confirm('确认删除该记录？', '提示', { type: 'warning' })
    .then(async () => { await deleteResource(props.config.api, row.id); ElMessage.success('删除成功'); fetchData() })
    .catch(() => {})
}
function onBatchDelete() {
  if (!selectedRows.value.length) return
  ElMessageBox.confirm(`确认删除选中的 ${selectedRows.value.length} 条记录？`, '批量删除', { type: 'warning' })
    .then(async () => {
      await Promise.all(selectedRows.value.map(row => deleteResource(props.config.api, row.id)))
      ElMessage.success('批量删除完成')
      selectedRows.value = []
      fetchData()
    })
    .catch(() => {})
}

async function openDetail(row) {
  if (props.config.detailPath) { router.push(props.config.detailPath + '/' + row.id); return }
  detailData.value = row
  detailLines.value = []
  detailLogs.value = []
  detailVisible.value = true
  if (props.config.detailable) {
    try {
      const res = await getDetail(props.config.api, row.id)
      const d = res.data || {}
      detailData.value = d
      detailLines.value = d.lines || d.items || d.details || []
      // 加载操作日志
      if (detailData.value.id) {
        operationLoading.value = true
        try {
          const businessType = resolveBusinessType(props.config.key)
          const businessId = detailData.value.id
          const resLog = await getOperationLogs(businessType, businessId)
          const list = resLog.data?.list || resLog.data?.records || (Array.isArray(resLog.data) ? resLog.data : [])
          if (Array.isArray(list) && list.length) {
            detailLogs.value = list.map(item => ({
              username: item.operatorName,
              action: item.action,
              changes: formatChangeJson(item.changeJson),
              atTime: item.createdAt
            }))
          } else {
            detailLogs.value = []
          }
          operationLoading.value = false
        } catch (e) {
          console.error('加载操作日志失败', e)
          detailLogs.value = []
          operationLoading.value = false
        }
      } else {
        detailLogs.value = d.auditLogs || d.logs || []
      }
    } catch (e) { /* 用列表行兜底 */ }
  }
}

const BUSINESS_TYPE_MAP = {
  products: 'product',
  categories: 'productCategory',
  dealers: 'dealer',
  hospitals: 'hospital',
  warehouses: 'warehouse',
  suppliers: 'supplier',
  orders: 'order',
  'purchase-orders': 'purchaseOrder',
  'sales-returns': 'salesReturn',
  'purchase-returns': 'purchaseReturn',
  authorizations: 'authorization',
  'sales-outs': 'salesOut',
  receipts: 'receipt',
  'stock-moves': 'stockMove',
  'inventory-adjustments': 'inventoryAdjustment',
  promotions: 'promotion',
  'surgery-reports': 'surgeryReport'
}

function resolveBusinessType(key) {
  return BUSINESS_TYPE_MAP[key] || key
}

function formatChangeJson(json) {
  if (!json) return ''
  try {
    const obj = typeof json === 'string' ? JSON.parse(json) : json
    if (!obj || typeof obj !== 'object') return ''
    return Object.entries(obj).map(([field, diff]) => {
      const o = diff && diff.old != null ? String(diff.old) : '-'
      const n = diff && diff.new != null ? String(diff.new) : '-'
      return `${labelOf(field) || field}: ${o} → ${n}`
    }).join('\n')
  } catch (e) {
    return typeof json === 'string' ? json : ''
  }
}

function openFilter(col, event) {
  currentFilterCol.value = col
  filterTriggerRef.value = event && event.currentTarget ? event.currentTarget : null
  filterPopoverVisible.value = !filterPopoverVisible.value
}

function applyFilter() {
  filterPopoverVisible.value = false
  reload()
}

function clearCurrentFilter() {
  if (currentFilterCol.value) {
    colFilters[currentFilterCol.value.k] = ''
  }
  filterPopoverVisible.value = false
  reload()
}

function handleImport() {
  importVisible.value = true
}

function getAuthHeader() {
  return { 'Authorization': 'Bearer ' + (getToken() || '') }
}

function buildExportQuery() {
  const sp = new URLSearchParams()
  if (keyword.value && String(keyword.value).trim()) sp.set('keyword', String(keyword.value).trim())
  Object.keys(colFilters || {}).forEach((k) => {
    const v = colFilters[k]
    if (v === '' || v == null) return
    if (Array.isArray(v) || typeof v === 'object') return
    sp.set(k, String(v))
  })
  const sortDir = sortOrder.value === 'ascending' ? 'asc' : 'desc'
  sp.set('sort', (sortField.value || 'updatedAt') + ',' + sortDir)
  return sp.toString()
}

function parseContentDisposition(cd) {
  if (!cd) return null
  const m1 = cd.match(/filename\*=UTF-8''([^;]+)/i)
  if (m1) {
    try { return decodeURIComponent(m1[1]) } catch (e) { return m1[1] }
  }
  const m2 = cd.match(/filename="?([^"]+)"?/i)
  if (m2) return m2[1]
  return null
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  setTimeout(() => {
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, 100)
}

async function handleExport() {
  const query = buildExportQuery()
  const url = props.config.api + '/actions/export' + (query ? ('?' + query) : '')
  try {
    const res = await fetch(url, { method: 'GET', headers: getAuthHeader() })
    if (!res.ok) {
      const text = await res.text().catch(() => '')
      let msg = '导出失败: HTTP ' + res.status
      try {
        const j = JSON.parse(text)
        if (j && j.message) msg = j.message
      } catch (e) {}
      throw new Error(msg)
    }
    const blob = await res.blob()
    if (!blob || blob.size === 0) {
      throw new Error('导出内容为空')
    }
    const serverName = parseContentDisposition(res.headers.get('content-disposition'))
    const filename = serverName || ((props.config.key || 'export') + '_' + new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19) + '.xlsx')
    downloadBlob(blob, filename)
    ElMessage.success('导出成功，共 ' + blob.size + ' 字节')
  } catch (e) {
    console.error('[Export] failed:', e)
    ElMessage.error(e.message || '导出失败')
  }
}

function beforeImport(file) {
  if (!file) return false
  const name = (file.name || '').toLowerCase()
  if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
    ElMessage.error('仅支持 .xlsx / .xls 文件')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 20MB')
    return false
  }
  return true
}

async function submitImport() {
  if (!uploadRef.value) return
  if (importing.value) return
  importing.value = true
  try {
    uploadRef.value.submit()
  } catch (e) {
    importing.value = false
    ElMessage.error(e.message || '提交导入失败')
  }
}

async function downloadTemplate() {
  if (tplDownloading.value) return
  tplDownloading.value = true
  try {
    const res = await fetch(props.config.api + '/actions/export/template', {
      method: 'GET',
      headers: getAuthHeader()
    })
    if (!res.ok) {
      const text = await res.text().catch(() => '')
      let msg = '下载模板失败: HTTP ' + res.status
      try {
        const j = JSON.parse(text)
        if (j && j.message) msg = j.message
      } catch (e) {}
      throw new Error(msg)
    }
    const blob = await res.blob()
    if (!blob || blob.size === 0) throw new Error('模板内容为空')
    const serverName = parseContentDisposition(res.headers.get('content-disposition'))
    const filename = serverName || ((props.config.key || 'template') + '_template.xlsx')
    downloadBlob(blob, filename)
    ElMessage.success('模板已下载')
  } catch (e) {
    console.error('[Template] failed:', e)
    ElMessage.error(e.message || '下载模板失败')
  } finally {
    tplDownloading.value = false
  }
}

function onImportSuccess(res, file) {
  importing.value = false
  let msg = '导入成功'
  if (res && typeof res === 'object') {
    if (res.message) msg = res.message
    else if (res.data) {
      if (res.data.message) msg = res.data.message
      else if (res.data.success !== undefined) {
        msg = `导入完成：成功 ${res.data.success} 条，失败 ${res.data.failed || 0} 条`
      }
    }
  }
  ElMessage.success(msg)
  importVisible.value = false
  fetchData()
}

function onImportError(err, file) {
  importing.value = false
  let msg = '导入失败'
  try {
    const status = err && err.status
    const raw = err && err.message ? String(err.message) : ''
    if (raw) {
      try {
        const j = JSON.parse(raw)
        if (j && j.message) msg = j.message
      } catch (e) {
        if (raw && raw.length < 200) msg = raw
      }
    }
    if (msg === '导入失败') {
      if (status === 404) msg = '导入接口不存在'
      else if (status === 403) msg = '没有导入权限'
      else if (status === 413) msg = '文件过大'
      else if (status === 500) msg = '服务器内部错误'
    }
  } catch (e) {}
  ElMessage.error(msg)
}
</script>

<style scoped>
.crud-container {
  background: var(--dms-bg-container);
  border-radius: 4px;
  padding: 16px;
  box-shadow: 0 1px 3px 0 rgb(0 0 0 / .1), 0 1px 2px -1px rgb(0 0 0 / .1);
}
.panel-title {
  margin-bottom: 16px;
  margin-top: 0;
  border-bottom: 1px solid var(--dms-border-1);
  padding-bottom: 8px;
  font-size: 1rem;
  color: var(--dms-color-primary);
  font-weight: 500;
}
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
.filter-icon { cursor: pointer; margin-left: 4px; font-size: 14px; }
.row-actions { display: flex; gap: 6px; align-items: center; flex-wrap: nowrap; }
.row-actions :deep(.el-button) { margin-left: 0; padding: 7px 8px; }

.crud-form-container { padding: 20px; max-width: 1200px; margin: 0 auto; }
.crud-form-container.has-lines { max-width: 100%; padding: 20px 24px; }
</style>




