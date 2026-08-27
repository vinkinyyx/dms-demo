<template>
  <div class="reports-overview">
    <el-card shadow="never" class="hero">
      <h2>报表总览</h2>
      <p>平台后台可查看跨租户汇总指标，并跳转各租户 PC 端的详细报表。</p>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="6" v-for="k in kpiCards" :key="k.key">
        <el-card shadow="hover" class="kpi-card" :style="{ borderTop: '3px solid ' + k.color }">
          <div class="kpi-v" :style="{ color: k.color }">{{ k.display }}</div>
          <div class="kpi-l">{{ k.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="links">
      <template #header>跳转各租户 PC 端报表中心</template>
      <el-form :inline="true">
        <el-form-item label="租户">
          <el-select v-model="selectedTenant" placeholder="选择租户" style="width: 320px" filterable>
            <el-option-group label="厂商租户">
              <el-option v-for="t in manufacturers" :key="t.id" :label="t.code + ' / ' + t.name" :value="t.id" />
            </el-option-group>
            <el-option-group label="经销商租户">
              <el-option v-for="t in dealers" :key="t.id" :label="t.code + ' / ' + t.name" :value="t.id" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!selectedTenant" @click="openReports">打开报表中心</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon>
        报表中心位于 PC 端 frontend-vue 内的 <code>/reports</code> 路由；admin-vue 仅做跨租户总览与跳转入口。
      </el-alert>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getTenantStats, listDealers, listManufacturers } from '@/api/admin'

const kpi = ref({})
const manufacturers = ref([])
const dealers = ref([])
const selectedTenant = ref(null)

const kpiCards = computed(() => ([
  { key: 'tenants', label: '租户总数', display: kpi.value.totalTenants || 0, color: '#1677ff' },
  { key: 'manufacturers', label: '厂商租户', display: kpi.value.manufacturerTenants || 0, color: '#52c41a' },
  { key: 'dealers', label: '经销商租户', display: kpi.value.dealerTenants || 0, color: '#faad14' },
  { key: 'active', label: '活跃租户', display: kpi.value.activeTenants || 0, color: '#ff4d4f' }
]))

async function load() {
  try {
    const [stats, manufacturerRes, dealerRes] = await Promise.all([
      getTenantStats(),
      listManufacturers({ page: 1, size: 200 }),
      listDealers({ page: 1, size: 200 })
    ])
    kpi.value = stats.data || {}
    manufacturers.value = manufacturerRes.data?.list || []
    dealers.value = dealerRes.data?.list || []
  } catch (e) {
    ElMessage.error('加载租户数据失败')
  }
}

function reportsBase() {
  const base = import.meta.env.BASE_URL || '/'
  return base.replace(/\/admin\/?$/, '/')
}
function openReports() {
  if (!selectedTenant.value) return
  const url = `${reportsBase()}reports?tenantId=${selectedTenant.value}`
  window.open(url, '_blank')
}

onMounted(load)
</script>

<style scoped>
.reports-overview { padding: 0; }
.hero { margin-bottom: 12px; }
.hero h2 { margin: 0 0 4px; }
.hero p { color: #666; margin: 0; }
.kpi-card { margin-bottom: 12px; text-align: center; }
.kpi-v { font-size: 24px; font-weight: 700; }
.kpi-l { font-size: 13px; color: var(--dms-text-4); margin-top: 6px; }
.links { margin-top: 12px; }
code { background: var(--dms-bg-page); padding: 1px 6px; border-radius: 3px; }
</style>