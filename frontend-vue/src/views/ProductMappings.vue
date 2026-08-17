<!--
  产品对码 — ListPageLayout 迁移示范（D13）
  说明：顶部"经销商租户"切换是页面级筛选（影响 fetchData 的 params），不属于 ListPageLayout 的搜索字段。
        按规范放在 ListPageLayout 上方。搜索区+表格+分页+行内操作全部走 ListPageLayout + v-has。
-->
<template>
  <div>
    <!-- 页面级：经销商租户切换（多租户厂家需要这个 context） -->
    <el-card shadow="never" class="ctx-card">
      <div class="ctx-bar">
        <span class="ctx-label">经销商租户</span>
        <el-select
          v-model="dealerTenantId"
          placeholder="选择经销商租户"
          clearable
          filterable
          style="width: 320px"
          @change="onDealerChange"
        >
          <el-option
            v-for="d in dealers"
            :key="d.tenantId"
            :label="`${d.name}（${d.dealerName || d.code}）`"
            :value="d.tenantId"
          />
        </el-select>
        <el-button @click="loadDealers">刷新租户</el-button>
      </div>
    </el-card>

    <ListPageLayout
      ref="layoutRef"
      page-key="product-mappings"
      :table-columns="tableColumns"
      :fetch-data="fetchData"
      :toolbar-actions="toolbarActions"
      :row-actions="rowActions"
      :initial-filter="initialFilter"
      style="margin-top: 12px"
    />

    <!-- 手工新增弹窗（保留原功能） -->
    <el-dialog v-model="createVisible" title="新增产品对码" width="520px">
      <el-form :model="form" label-width="140px">
        <el-form-item label="经销商租户">
          <el-select v-model="form.dealerTenantId" filterable placeholder="选择经销商租户" style="width:100%">
            <el-option v-for="d in dealers" :key="d.tenantId" :label="d.name" :value="d.tenantId" />
          </el-select>
        </el-form-item>
        <el-form-item label="厂家产品ID"><el-input v-model.number="form.manufacturerProductId" /></el-form-item>
        <el-form-item label="经销商产品ID"><el-input v-model.number="form.dealerProductId" /></el-form-item>
        <el-form-item label="包装单位"><el-input v-model="form.packageUnit" /></el-form-item>
        <el-form-item label="换算率"><el-input-number v-model="form.conversionRate" :min="0" :precision="4" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import ListPageLayout from '@/components/ListPageLayout.vue'
import {
  listProductMappings, createProductMapping, enableProductMapping, disableProductMapping,
  listMyDealerTenants, downloadTemplateUrl
} from '@/api/productMapping'

const layoutRef = ref(null)
const dealers = ref([])
const dealerTenantId = ref(null)
const createVisible = ref(false)
const form = reactive({ dealerTenantId: null, manufacturerProductId: null, dealerProductId: null, packageUnit: '', conversionRate: 1, remark: '' })

const initialFilter = { keyword: '' }

const tableColumns = [
  { prop: 'dealerTenantName', label: '经销商租户', width: 180 },
  { prop: 'manufacturerProductCode', label: '厂家产品编码', width: 180 },
  { prop: 'dealerProductCode', label: '经销商产品编码', width: 180 },
  { prop: 'packageUnit', label: '包装单位', width: 100 },
  { prop: 'conversionRate', label: '换算率', width: 100 },
  { prop: 'status', label: '状态', width: 100,
    render: (row) => row.status === 'active' ? '启用' : '停用' }
]

async function fetchData(params) {
  const res = await listProductMappings({
    page: params.page, size: params.size, keyword: params.keyword, dealerTenantId: dealerTenantId.value
  })
  // 适配 ListPageLayout 期望的数据格式
  return { data: { list: res.data.list, total: res.data.total } }
}

function onDealerChange() {
  layoutRef.value && layoutRef.value.load && layoutRef.value.load()
}

async function loadDealers() {
  const res = await listMyDealerTenants()
  dealers.value = res.data
}

function openCreate() {
  Object.assign(form, { dealerTenantId: null, manufacturerProductId: null, dealerProductId: null, packageUnit: '', conversionRate: 1, remark: '' })
  createVisible.value = true
}
async function save() {
  await createProductMapping(form)
  ElMessage.success('已保存')
  createVisible.value = false
  layoutRef.value && layoutRef.value.load && layoutRef.value.load()
}
function downloadTemplate() { window.open(downloadTemplateUrl) }
async function toggle(row, active) {
  active ? await enableProductMapping(row.id) : await disableProductMapping(row.id)
  ElMessage.success('已更新')
  layoutRef.value && layoutRef.value.load && layoutRef.value.load()
}

const toolbarActions = {
  import: () => ElMessage.info('请使用页面级导入入口（暂未迁移到 ListPageLayout）'),
  export: () => ElMessage.info('导出待接入'),
  create: openCreate
}

const rowActions = {
  // "启用/停用"是一个按钮，key 与 ListPageLayout buttonKey 对应；为避免冲突，这里用 view 表示
  view: (row) => toggle(row, row.status !== 'active')
}

onMounted(loadDealers)
</script>

<style scoped>
.ctx-card { padding: 0; }
.ctx-bar { display: flex; align-items: center; gap: 8px; }
.ctx-label { color: var(--dms-text-3); font-size: 13px; }
</style>