<template>
  <div class="tenant-config-page">
    <div class="panel-title">列表页配置</div>
    <div class="page-toolbar">
      <el-select v-model="pageKey" filterable placeholder="选择页面" style="width: 280px" @change="loadPage">
        <el-option v-for="p in pageOptions" :key="p.key" :label="p.label" :value="p.key" />
      </el-select>
      <el-button type="primary" @click="loadPage" :disabled="!pageKey || loading">查询</el-button>
      <el-button @click="loadPage" :disabled="!pageKey || loading">重置</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="saveAll" :disabled="!pageKey || saving">保存本页覆盖</el-button>
    </div>

    <el-tabs v-model="activeTab" v-if="pageKey" v-loading="loading">
      <el-tab-pane label="搜索字段" name="filters">
        <el-table :data="filters" border size="small">
          <el-table-column prop="filterKey" label="字段 Key" width="180" />
          <el-table-column prop="label" label="名称" width="180" />
          <el-table-column prop="componentType" label="控件" width="120" />
          <el-table-column label="显示" width="100">
            <template #default="{ row }"><el-switch v-model="row.visible" /></template>
          </el-table-column>
          <el-table-column label="排序" width="160">
            <template #default="{ row }"><el-input-number v-model="row.sortOrder" :min="1" :max="999" controls-position="right" /></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="工具栏按钮" name="toolbar">
        <el-alert type="info" :closable="false" show-icon class="fixed-tip" title="查询、重置为列表页固定按钮，始终显示。" />
        <el-table :data="toolbarButtons" border size="small">
          <el-table-column prop="buttonKey" label="按钮 Key" width="150" />
          <el-table-column prop="label" label="名称" width="150" />
          <el-table-column prop="permissionCode" label="权限码" />
          <el-table-column label="显示" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.visible" :disabled="isFixedToolbarButton(row)" />
            </template>
          </el-table-column>
          <el-table-column label="排序" width="160">
            <template #default="{ row }"><el-input-number v-model="row.sortOrder" :min="1" :max="999" controls-position="right" :disabled="isFixedToolbarButton(row)" /></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="行内按钮" name="row">
        <el-table :data="rowButtons" border size="small">
          <el-table-column prop="buttonKey" label="按钮 Key" width="150" />
          <el-table-column prop="label" label="名称" width="150" />
          <el-table-column prop="permissionCode" label="权限码" />
          <el-table-column label="显示" width="100">
            <template #default="{ row }"><el-switch v-model="row.visible" /></template>
          </el-table-column>
          <el-table-column label="排序" width="160">
            <template #default="{ row }"><el-input-number v-model="row.sortOrder" :min="1" :max="999" controls-position="right" /></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getPageLayout, saveTenantFilters, saveTenantButtons } from '@/api/admin'

const pageOptions = [
  { key: 'products', label: '产品管理' }, { key: 'categories', label: '产品分类' }, { key: 'dealers', label: '经销商管理' },
  { key: 'hospitals', label: '医院/终端' }, { key: 'warehouses', label: '仓库管理' }, { key: 'suppliers', label: '供应商' },
  { key: 'contracts', label: '合同工作台' }, { key: 'authorizations', label: '授权管理' },
  { key: 'orders', label: '销售订单' }, { key: 'sales-returns', label: '销退订单' }, { key: 'purchase-orders', label: '采购订单' },
  { key: 'purchase-returns', label: '采退订单' }, { key: 'inventory', label: '库存查询' }, { key: 'sales-outs', label: '销售出库' },
  { key: 'receipts', label: '收货入库' }, { key: 'stock-moves', label: '库存移动' }, { key: 'inventory-adjustments', label: '库存调整' },
  { key: 'surgery-reports', label: '手术植入报台' }, { key: 'promotions', label: '促销规则' }, { key: 'dealer-profile', label: '经销商画像' },
  { key: 'positions', label: '销售岗位' }, { key: 'users', label: '账号管理' }, { key: 'roles', label: '角色权限' },
  { key: 'api-call-log', label: '接口调用日志' }, { key: 'product-mappings', label: '产品对码' }
]
const pageKey = ref('orders')
const activeTab = ref('filters')
const filters = ref([])
const toolbarButtons = ref([])
const rowButtons = ref([])
const saving = ref(false)
const loading = ref(false)

function normalizeButton(button, scope) {
  return {
    ...button,
    scope,
    buttonType: button.buttonType || 'default',
    rowButtonPosition: button.rowButtonPosition || (scope === 'row' ? 'common' : undefined),
    confirmRequired: Boolean(button.confirmRequired),
    visible: isFixedToolbarButton({ scope, buttonKey: button.buttonKey }) ? true : button.visible !== false
  }
}

function isFixedToolbarButton(row) {
  return row?.scope === 'toolbar' && ['search', 'reset'].includes(row.buttonKey)
}

async function loadPage() {
  if (!pageKey.value) return
  loading.value = true
  try {
    const res = await getPageLayout(pageKey.value)
    const d = res.data || {}
    filters.value = (d.filters || []).map(f => ({ ...f }))
    toolbarButtons.value = (d.toolbar || []).map(b => normalizeButton(b, 'toolbar'))
    rowButtons.value = (d.rowButtons || []).map(b => normalizeButton(b, 'row'))
  } finally {
    loading.value = false
  }
}

async function saveAll() {
  saving.value = true
  try {
    const buttons = [
      ...toolbarButtons.value.map(b => ({ ...b, visible: isFixedToolbarButton(b) ? true : b.visible })),
      ...rowButtons.value.map(b => ({ ...b }))
    ]
    await saveTenantFilters(pageKey.value, filters.value)
    await saveTenantButtons(pageKey.value, buttons)
    ElMessage.success('租户覆盖已保存')
    await loadPage()
  } finally {
    saving.value = false
  }
}

onMounted(loadPage)
</script>

<style scoped>
.tenant-config-page { background: var(--dms-bg-container); border-radius: 4px; padding: 16px; box-shadow: 0 1px 3px rgb(0 0 0 / .1); }
.panel-title { margin-bottom: 16px; border-bottom: 1px solid var(--dms-border-1); padding-bottom: 8px; font-size: 1rem; color: var(--dms-color-primary); font-weight: 500; }
.page-toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.fixed-tip { margin-bottom: 12px; }
.spacer { flex: 1; }
</style>