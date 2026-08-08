<template>
  <div class="page">
    <div class="toolbar">
      <el-select v-model="tenantType" style="width:140px">
        <el-option label="厂家" value="MANUFACTURER" /><el-option label="经销商" value="DEALER" />
      </el-select>
      <el-input v-model="pageKey" placeholder="页面Key，如 products" style="width:220px" />
      <el-radio-group v-model="tab">
        <el-radio-button label="page">字段配置</el-radio-button>
        <el-radio-button label="filter">筛选配置</el-radio-button>
        <el-radio-button label="button">按钮配置</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="refreshCache">刷新缓存</el-button>
    </div>

    <!-- 字段 / 筛选配置保持原样 -->
    <template v-if="tab !== 'button'">
      <el-button type="primary" style="margin-bottom:12px" @click="addRow">新增一行</el-button>
      <el-table :data="rows" border>
        <el-table-column :label="tab==='page'?'字段Key':'筛选Key'" width="180">
          <template #default="{ row }"><el-input v-model="row.key" /></template>
        </el-table-column>
        <el-table-column label="标签" width="180"><template #default="{ row }"><el-input v-model="row.label" /></template></el-table-column>
        <el-table-column v-if="tab==='page'" label="可见" width="80"><template #default="{ row }"><el-switch v-model="row.visible" /></template></el-table-column>
        <el-table-column v-if="tab==='page'" label="只读" width="80"><template #default="{ row }"><el-switch v-model="row.readonly" /></template></el-table-column>
        <el-table-column v-if="tab==='page'" label="必填" width="80"><template #default="{ row }"><el-switch v-model="row.required" /></template></el-table-column>
        <el-table-column v-if="tab==='page'" label="可导出" width="90"><template #default="{ row }"><el-switch v-model="row.exportable" /></template></el-table-column>
        <el-table-column v-if="tab==='page'" label="宽度/排序"><template #default="{ row }"><el-input-number v-model="row.sortOrder" :min="0" style="width:100px" /></template></el-table-column>
        <el-table-column v-if="tab==='filter'" label="组件类型" width="160"><template #default="{ row }"><el-input v-model="row.componentType" placeholder="input/select/date" /></template></el-table-column>
        <el-table-column v-if="tab==='filter'" label="字典类型" width="160"><template #default="{ row }"><el-input v-model="row.dictType" /></template></el-table-column>
        <el-table-column v-if="tab==='filter'" label="多选" width="80"><template #default="{ row }"><el-switch v-model="row.multiple" /></template></el-table-column>
        <el-table-column v-if="tab==='filter'" label="排序"><template #default="{ row }"><el-input-number v-model="row.sortOrder" :min="0" style="width:100px" /></template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ $index }"><el-button link type="danger" @click="rows.splice($index,1)">删除</el-button></template></el-table-column>
      </el-table>
      <el-button type="primary" style="margin-top:12px" @click="save">保存配置</el-button>
    </template>

    <!-- 按钮配置 Tab：D13 -->
    <template v-else>
      <el-alert type="info" :closable="false" style="margin-bottom:12px" title="平台默认对所有租户生效；租户覆盖只对当前租户生效。PLATFORM_DEFAULT 模式保存会替换平台默认；TENANT_OVERRIDE 模式保存只对当前租户生效。" />
      <el-radio-group v-model="scopeLevel" style="margin-bottom:12px">
        <el-radio-button label="PLATFORM_DEFAULT">平台默认</el-radio-button>
        <el-radio-button label="TENANT_OVERRIDE">租户覆盖</el-radio-button>
      </el-radio-group>
      <el-button type="primary" style="margin-bottom:12px" @click="addButtonRow">新增按钮</el-button>
      <el-table :data="buttonRows" border>
        <el-table-column label="作用域" width="100">
          <template #default="{ row }">
            <el-select v-model="row.scope">
              <el-option label="顶部" value="toolbar" />
              <el-option label="行内" value="row" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="Key" width="160"><template #default="{ row }"><el-input v-model="row.buttonKey" placeholder="search/reset/create/view/edit/delete" /></template></el-table-column>
        <el-table-column label="显示文字" width="120"><template #default="{ row }"><el-input v-model="row.label" /></template></el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-select v-model="row.buttonType">
              <el-option v-for="t in ['primary','default','danger','warning','info','success']" :key="t" :label="t" :value="t" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="权限码" width="220"><template #default="{ row }"><el-input v-model="row.permissionCode" placeholder="如 products:export" /></template></el-table-column>
        <el-table-column label="可见" width="80"><template #default="{ row }"><el-switch v-model="row.visible" /></template></el-table-column>
        <el-table-column label="排序" width="120"><template #default="{ row }"><el-input-number v-model="row.sortOrder" :min="0" style="width:100px" /></template></el-table-column>
        <el-table-column label="行内分组" width="120">
          <template #default="{ row }">
            <el-select v-model="row.rowButtonPosition" :disabled="row.scope !== 'row'">
              <el-option label="常用" value="common" /><el-option label="危险" value="danger" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="二次确认" width="100"><template #default="{ row }"><el-switch v-model="row.confirmRequired" /></template></el-table-column>
        <el-table-column label="来源" width="100"><template #default="{ row }"><el-tag v-if="row.fromTenant" type="warning" size="small">租户覆盖</el-tag><el-tag v-else type="info" size="small">平台默认</el-tag></template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ $index }"><el-button link type="danger" @click="buttonRows.splice($index,1)">删除</el-button></template></el-table-column>
      </el-table>
      <el-button type="primary" style="margin-top:12px" @click="saveButtons">保存按钮配置</el-button>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPageConfigs, upsertPageConfigs, getFilterConfigs, upsertFilterConfigs, refreshUiCache, getButtonConfigs, upsertButtonConfigs, refreshButtonCache } from '@/api/admin'

const tenantType = ref('MANUFACTURER'); const pageKey = ref('products'); const tab = ref('page'); const scopeLevel = ref('PLATFORM_DEFAULT')
const rows = ref([]); const buttonRows = ref([])

async function load() {
  if (!pageKey.value) { ElMessage.warning('请输入页面Key'); return }
  if (tab.value === 'page') {
    const res = await getPageConfigs({ pageKey: pageKey.value, tenantType: tenantType.value })
    rows.value = (res.data || []).map(r => ({ key: r.fieldKey, label: r.label, visible: r.visible, readonly: r.readonly, required: r.required, exportable: r.exportable, sortOrder: r.sortOrder, id: r.id }))
  } else if (tab.value === 'filter') {
    const res = await getFilterConfigs({ pageKey: pageKey.value, tenantType: tenantType.value })
    rows.value = (res.data || []).map(r => ({ key: r.filterKey, label: r.label, componentType: r.componentType, dictType: r.dictType, multiple: r.multiple, sortOrder: r.sortOrder, id: r.id }))
  } else {
    const res = await getButtonConfigs({ pageKey: pageKey.value, tenantType: tenantType.value })
    buttonRows.value = (res.data || []).map(b => ({
      id: b.id, scope: b.scope, buttonKey: b.buttonKey, label: b.label, buttonType: b.buttonType || 'default',
      permissionCode: b.permissionCode, visible: b.visible !== false, sortOrder: b.sortOrder || 100,
      rowButtonPosition: b.rowButtonPosition || 'common', confirmRequired: !!b.confirmRequired, fromTenant: !!b.fromTenant
    }))
  }
}
function addRow() {
  if (tab.value === 'page') rows.value.push({ key: '', label: '', visible: true, readonly: false, required: false, exportable: true, sortOrder: 100 })
  else if (tab.value === 'filter') rows.value.push({ key: '', label: '', componentType: 'input', dictType: '', multiple: false, sortOrder: 100 })
}
function addButtonRow() {
  buttonRows.value.push({ scope: 'toolbar', buttonKey: '', label: '', buttonType: 'default', permissionCode: '', visible: true, sortOrder: 100, rowButtonPosition: 'common', confirmRequired: false })
}
async function save() {
  if (tab.value === 'page') {
    const fields = rows.value.map(r => ({ fieldKey: r.key, label: r.label, visible: r.visible, readonly: r.readonly, required: r.required, exportable: r.exportable, sortOrder: r.sortOrder, config: {} }))
    await upsertPageConfigs({ pageKey: pageKey.value, tenantType: tenantType.value, fields })
  } else if (tab.value === 'filter') {
    const filters = rows.value.map(r => ({ filterKey: r.key, label: r.label, componentType: r.componentType, dictType: r.dictType, multiple: r.multiple, visible: true, sortOrder: r.sortOrder }))
    await upsertFilterConfigs({ pageKey: pageKey.value, tenantType: tenantType.value, filters })
  }
  ElMessage.success('已保存'); load()
}
async function saveButtons() {
  if (!pageKey.value) { ElMessage.warning('请输入页面Key'); return }
  await upsertButtonConfigs({ pageKey: pageKey.value, tenantType: tenantType.value, scopeLevel: scopeLevel.value, buttons: buttonRows.value })
  ElMessage.success('按钮配置已保存'); load()
}
async function refreshCache() {
  await refreshUiCache(); if (tab.value === 'button') await refreshButtonCache(); ElMessage.success('缓存已刷新')
}
onMounted(load)
</script>
