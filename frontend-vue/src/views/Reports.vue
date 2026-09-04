<template>
  <div class="reports">
    <el-card shadow="never" class="hero">
      <div class="hero-row">
        <div>
          <h2 class="hero-title">报表中心</h2>
          <p class="hero-sub">{{ heroSub }}</p>
        </div>
        <div class="hero-right">
          <el-tag type="info" effect="plain">v4.2</el-tag>
        </div>
      </div>
    </el-card>

    <ReportPage
      v-if="activeKey"
      :key="activeKey"
      :meta="REPORTS[activeKey]"
      style="margin-top: 12px"
      @back="activeKey = null"
    />

    <template v-else>
      <el-row :gutter="12">
        <el-col v-for="g in visibleGroups" :key="g.title" :span="8">
          <el-card shadow="never" class="group-card">
            <template #header>
              <div class="group-header">
                <el-icon><component :is="g.icon" /></el-icon>
                <span>{{ g.title }}</span>
                <el-tag size="small" :type="g.keys.length >= 3 ? 'success' : 'info'">{{ g.keys.length }} 张</el-tag>
              </div>
            </template>
            <div class="group-items">
              <div
                v-for="k in g.keys"
                :key="k"
                class="group-item"
                @click="open(k)"
              >
                <div class="gi-row">
                  <el-icon class="gi-icon"><component :is="REPORTS[k].icon" /></el-icon>
                  <span class="gi-name">{{ REPORTS[k].title }}</span>
                  <el-tag v-if="REPORTS[k].placeholder" type="warning" size="small" effect="plain">接口待补</el-tag>
                </div>
                <div class="gi-desc">{{ REPORTS[k].desc }}</div>
                <div class="gi-meta">
                  <span>{{ rangeLabel(REPORTS[k].defaultRange) }}</span>
                  <span>·</span>
                  <span>{{ REPORTS[k].filters.length }} 个筛选</span>
                  <span>·</span>
                  <span>{{ REPORTS[k].cols.length }} 列</span>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import ReportPage from '@/components/ReportPage.vue'
import { REPORTS, REPORT_GROUPS, rangeFor } from '@/config/reports'
import { useTenantFeatures } from '@/composables/useTenantFeatures'

const { features: tenantFeatures, loadFeatures } = useTenantFeatures()
const inventoryEnabled = computed(() => !!tenantFeatures.value.inventoryEnabled)

// 库存类报表在库存模块关闭时整体隐藏（key 集合来自 config/reports 中 group='库存类'）
const INVENTORY_REPORT_KEYS = Object.keys(REPORTS).filter(k => REPORTS[k].group === '库存类')
const visibleGroups = computed(() =>
  REPORT_GROUPS
    .map(g => ({ ...g, keys: g.keys.filter(k => inventoryEnabled.value || !INVENTORY_REPORT_KEYS.includes(k)) }))
    .filter(g => g.keys.length > 0)
)
const heroSub = computed(() => {
  const parts = ['销售', '订单', '应收', '报台', '经销商画像']
  if (inventoryEnabled.value) parts.splice(1, 0, '库存')
  return parts.join(' · ') + ' 统一入口。每张报表均支持筛选、图表、穿透、xlsx 导出。'
})

const activeKey = ref(null)
const route = useRoute()
onMounted(async () => {
  await loadFeatures()
  const k = route.query.key
  // 库存报表在模块关闭时即使带 query 也不打开
  if (k && REPORTS[k] && (inventoryEnabled.value || !INVENTORY_REPORT_KEYS.includes(k))) activeKey.value = k
})

function open(k) { activeKey.value = k }

const RANGE_LABELS = { today: '当日', '7d': '近 7 天', '30d': '近 30 天', '90d': '近 90 天', week: '本周', month: '本月', quarter: '本季', year: '本年', none: '不限时间' }
function rangeLabel(k) { return RANGE_LABELS[k] || k }
</script>

<style scoped>
.reports { padding: 0; }
.hero { margin-bottom: 12px; }
.hero-row { display: flex; justify-content: space-between; align-items: center; }
.hero-title { margin: 0 0 4px; font-size: 20px; }
.hero-sub { color: #666; margin: 0; line-height: 1.6; }
.group-card { margin-bottom: 12px; }
.group-header { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.group-items { display: flex; flex-direction: column; gap: 10px; }
.group-item { padding: 12px 14px; border: 1px solid var(--dms-border-2); border-radius: 6px; cursor: pointer; transition: all .15s; }
.group-item:hover { border-color: var(--dms-color-primary); background: var(--dms-color-primary-bg); transform: translateY(-1px); }
.gi-row { display: flex; align-items: center; gap: 8px; }
.gi-icon { color: var(--dms-color-primary); }
.gi-name { font-size: 14px; font-weight: 500; }
.gi-desc { color: var(--dms-text-4); font-size: 12px; margin-top: 6px; line-height: 1.5; }
.gi-meta { color: var(--dms-text-placeholder); font-size: 12px; margin-top: 6px; display: flex; gap: 4px; }
</style>

