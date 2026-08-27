<template>
  <div class="overview-page">
    <el-card shadow="never" class="welcome-card">
      <div class="welcome">
        <div>
          <h2>平台总览</h2>
          <p>DMS 平台后台统一展示租户、管理员与平台配置情况。</p>
        </div>
        <el-space wrap>
          <el-button type="primary" @click="go('/tenants/manufacturers')">厂家租户</el-button>
          <el-button @click="go('/tenants/dealers')">经销商租户</el-button>
          <el-button @click="go('/reports')">报表总览</el-button>
        </el-space>
      </div>
    </el-card>

    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in statCards" :key="card.key" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">{{ formatNumber(card.value) }}</div>
          <div class="stat-desc">{{ card.desc }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>快捷入口</template>
          <div class="quick-links">
            <el-button v-for="item in quickLinks" :key="item.path" plain @click="go(item.path)">{{ item.label }}</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>系统状态</template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="厂家租户">{{ stats.manufacturerTenants ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="经销商租户">{{ stats.dealerTenants ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="启用租户">{{ stats.activeTenants ?? 0 }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTenantStats, listTenantAdmins } from '@/api/admin'

const router = useRouter()
const loading = ref(false)
const stats = ref({})
const adminCount = ref(0)

const statCards = computed(() => [
  { key: 'total', label: '租户总数', value: stats.value.totalTenants, desc: '厂家 + 经销商租户', color: '#1f2d3d' },
  { key: 'manufacturers', label: '厂家租户', value: stats.value.manufacturerTenants, desc: '平台入驻厂家', color: '#1677ff' },
  { key: 'dealers', label: '经销商租户', value: stats.value.dealerTenants, desc: '绑定厂家的经销商', color: '#00b96b' },
  { key: 'admins', label: '租户管理员', value: adminCount.value, desc: '已创建管理员账号', color: '#722ed1' },
  { key: 'active', label: '启用租户', value: stats.value.activeTenants, desc: '当前启用状态', color: '#52c41a' },
  { key: 'disabled', label: '停用租户', value: Math.max((stats.value.totalTenants || 0) - (stats.value.activeTenants || 0), 0), desc: '当前停用状态', color: '#909399' },
  { key: 'apiToday', label: '今日接口调用量', value: stats.value.todayApiCalls, desc: '暂无统计接口时显示 -', color: '#fa8c16' }
])

const quickLinks = [
  { label: '厂家租户', path: '/tenants/manufacturers' },
  { label: '经销商租户', path: '/tenants/dealers' },
  { label: '租户管理员', path: '/tenant-admins' },
  { label: '角色模板', path: '/role-templates' },
  { label: '平台菜单', path: '/menus' },
  { label: '全局字典', path: '/dicts' },
  { label: '接口日志', path: '/logs/api' },
  { label: '审计日志', path: '/logs/audits' }
]

function formatNumber(value) {
  return value === undefined || value === null ? '-' : value
}
function go(path) {
  router.push(path)
}
async function load() {
  loading.value = true
  try {
    const [statsRes, adminRes] = await Promise.all([
      getTenantStats(),
      listTenantAdmins({ page: 1, size: 1 })
    ])
    stats.value = statsRes.data || {}
    adminCount.value = adminRes.data?.total ?? 0
  } catch (e) {
    ElMessage.error('加载平台总览失败')
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.overview-page { display: flex; flex-direction: column; gap: 16px; }
.welcome-card { background: linear-gradient(135deg, #ffffff 0%, #f5f8fc 100%); }
.welcome { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.welcome h2 { margin: 0 0 8px; color: #1f2d3d; }
.welcome p { margin: 0; color: #606266; }
.stat-row { margin: 0; }
.stat-card { margin-bottom: 16px; }
.stat-label { color: #606266; font-size: 14px; }
.stat-value { margin-top: 12px; font-size: 30px; font-weight: 700; line-height: 1.2; }
.stat-desc { margin-top: 8px; color: #909399; font-size: 12px; }
.quick-links { display: flex; flex-wrap: wrap; gap: 10px; }
:global(html[data-mode='dark']) .welcome-card { background: linear-gradient(135deg, #111827 0%, #111c33 100%); }
:global(html[data-mode='dark']) .welcome h2 { color: #f8fafc; }
:global(html[data-mode='dark']) .welcome p { color: #aab8cc; }
</style>