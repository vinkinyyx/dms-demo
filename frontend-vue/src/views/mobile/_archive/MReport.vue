<template>
  <div>
    <van-nav-bar title="数据报表" />
    <van-tabs v-model:active="activeTab" sticky>
      <van-tab title="销售统计" name="sales">
        <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
          <div class="report-body">
            <van-grid :column-num="2" :border="false" class="kpi-grid">
              <van-grid-item v-for="k in salesKpis" :key="k.key">
                <div class="kpi-v">{{ k.value }}</div>
                <div class="kpi-l">{{ k.label }}</div>
              </van-grid-item>
            </van-grid>

            <div class="sec-title">销售趋势（近7天）</div>
            <van-cell-group inset>
              <van-cell v-for="(t, idx) in salesTrend" :key="idx" :title="t.date" :value="'¥ ' + (t.amount || 0)" />
              <van-empty v-if="!salesTrend.length" description="暂无数据" />
            </van-cell-group>

            <div class="sec-title">TOP经销商</div>
            <van-cell-group inset>
              <van-cell v-for="(d, idx) in topDealers" :key="idx" :title="d.dealerName || d.name"
                :value="'¥ ' + (d.amount || 0)">
                <template #icon><van-tag :type="idx < 3 ? 'danger' : 'primary'" style="margin-right:8px">{{ idx + 1 }}</van-tag></template>
              </van-cell>
              <van-empty v-if="!topDealers.length" description="暂无数据" />
            </van-cell-group>
          </div>
        </van-pull-refresh>
      </van-tab>

      <van-tab title="库存预警" name="inventory">
        <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
          <div class="report-body">
            <van-grid :column-num="2" :border="false" class="kpi-grid">
              <van-grid-item v-for="k in invKpis" :key="k.key">
                <div class="kpi-v">{{ k.value }}</div>
                <div class="kpi-l">{{ k.label }}</div>
              </van-grid-item>
            </van-grid>

            <div class="sec-title">库存分布</div>
            <van-cell-group inset>
              <van-cell v-for="(p, idx) in inventoryPie" :key="idx" :title="p.warehouseName || p.name || '仓库'"
                :value="p.qty || p.value || 0" />
              <van-empty v-if="!inventoryPie.length" description="暂无数据" />
            </van-cell-group>

            <div class="sec-title">低库存预警（安全库存以下）</div>
            <van-cell-group inset>
              <van-cell v-for="(it, idx) in lowStockList" :key="idx" :title="it.productName || it.productCode"
                :label="'仓库：' + (it.warehouseName || '-') + ' · 安全库存：' + (it.safetyQty || 0)"
                :value="'当前 ' + (it.qty || 0)">
                <template #value>
                  <van-tag type="danger">低库存</van-tag>
                  <div class="sub-val">{{ it.qty || 0 }}</div>
                </template>
              </van-cell>
              <van-empty v-if="!lowStockList.length" description="暂无低库存预警" />
            </van-cell-group>
          </div>
        </van-pull-refresh>
      </van-tab>

      <van-tab title="采购统计" name="purchase">
        <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
          <div class="report-body">
            <van-grid :column-num="2" :border="false" class="kpi-grid">
              <van-grid-item v-for="k in purchaseKpis" :key="k.key">
                <div class="kpi-v">{{ k.value }}</div>
                <div class="kpi-l">{{ k.label }}</div>
              </van-grid-item>
            </van-grid>

            <div class="sec-title">最近采购订单</div>
            <van-cell-group inset>
              <van-cell v-for="po in recentPo" :key="po.id" :title="po.code"
                :label="(po.supplierName || '-') + ' · ' + fmt(po.createdAt, 'createdAt')"
                :value="'¥ ' + (po.finalAmount || 0)">
                <template #value>
                  <van-tag :type="statusTagType(po.status)">{{ statusText(po.status) }}</van-tag>
                  <div class="sub-val">¥ {{ po.finalAmount || 0 }}</div>
                </template>
              </van-cell>
              <van-empty v-if="!recentPo.length" description="暂无数据" />
            </van-cell-group>

            <div class="sec-title">订单漏斗</div>
            <van-cell-group inset>
              <van-cell v-for="(f, idx) in orderFunnel" :key="idx" :title="f.stage || f.label || f.name"
                :value="f.count || f.value || 0" />
              <van-empty v-if="!orderFunnel.length" description="暂无数据" />
            </van-cell-group>
          </div>
        </van-pull-refresh>
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { showToast } from 'vant'
import { listResource } from '@/api/crud'
import { getKpi, getSalesTrend, getInventoryPie, getTopDealers, getOrderFunnel } from '@/api/dashboard'
import { statusText, statusTagType, fmt } from '@/utils/dict'

const activeTab = ref('sales')
const refreshing = ref(false)

const kpiData = ref({})
const salesTrend = ref([])
const topDealers = ref([])
const inventoryPie = ref([])
const lowStockList = ref([])
const recentPo = ref([])
const orderFunnel = ref([])

const salesKpis = computed(() => [
  { key: 'salesAmount', label: '本月销售', value: kpiData.value.salesAmount ?? '-' },
  { key: 'salesOrders', label: '销售订单数', value: kpiData.value.salesOrders ?? '-' }
])

const invKpis = computed(() => [
  { key: 'totalSku', label: 'SKU总数', value: kpiData.value.totalSku ?? '-' },
  { key: 'lowStock', label: '低库存数', value: lowStockList.value.length }
])

const purchaseKpis = computed(() => [
  { key: 'poCount', label: '本月采购单', value: recentPo.value.length > 0 ? recentPo.value.length : '-' },
  { key: 'poAmount', label: '采购金额', value: kpiData.value.purchaseAmount ?? '-' }
])

async function loadAll() {
  try {
    const r = await getKpi()
    kpiData.value = r.data || {}
  } catch (e) { /* ignore */ }

  try {
    const r = await getSalesTrend()
    salesTrend.value = r.data || []
  } catch (e) { /* ignore */ }

  try {
    const r = await getTopDealers()
    topDealers.value = r.data || []
  } catch (e) { /* ignore */ }

  try {
    const r = await getInventoryPie()
    inventoryPie.value = r.data || []
  } catch (e) { /* ignore */ }

  try {
    const r = await getOrderFunnel()
    orderFunnel.value = r.data || []
  } catch (e) { /* ignore */ }

  try {
    const r = await listResource('/api/inventory', { page: 1, size: 100 })
    const d = r.data
    const list = Array.isArray(d) ? d : (d.list || d.records || [])
    lowStockList.value = list.filter(it => (it.safetyQty != null) && (it.qty || 0) < it.safetyQty).slice(0, 20)
  } catch (e) { /* ignore */ }

  try {
    const r = await listResource('/api/purchase-orders', { page: 1, size: 10 })
    const d = r.data
    recentPo.value = Array.isArray(d) ? d : (d.list || d.records || [])
  } catch (e) { /* ignore */ }
}

async function onRefresh() {
  await loadAll()
  refreshing.value = false
  showToast('已刷新')
}

loadAll()
</script>

<style scoped>
.report-body { padding-bottom: 20px; }
.kpi-grid { margin-top: 10px; }
.kpi-v { font-size: 20px; font-weight: 700; color: #2C4B8E; }
.kpi-l { font-size: 12px; color: #969799; margin-top: 4px; }
.sec-title { font-size: 15px; font-weight: 600; margin: 16px 16px 8px; color: #323233; }
.sub-val { color: #ee0a24; font-weight: 600; font-size: 13px; margin-top: 2px; }
</style>
