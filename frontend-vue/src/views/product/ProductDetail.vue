<template>
  <div>
    <el-card shadow="never" class="pd-head">
      <el-button :icon="ArrowLeft" link @click="$router.back()">返回</el-button>
      <span v-if="info" class="pd-title">{{ info.name_cn || info.nameCn }}（{{ info.code }}）</span>
      <span v-else class="pd-title">产品详情</span>
    </el-card>

    <el-row :gutter="12" v-if="info">
      <el-col :span="8">
        <el-card shadow="never" class="pd-side">
          <div class="side-row"><span class="lbl">编码</span><span>{{ info.code }}</span></div>
          <div class="side-row"><span class="lbl">中文名</span><span>{{ info.name_cn || info.nameCn || '-' }}</span></div>
          <div class="side-row"><span class="lbl">英文名</span><span>{{ info.name_en || info.nameEn || '-' }}</span></div>
          <div class="side-row"><span class="lbl">规格</span><span>{{ info.spec || '-' }}</span></div>
          <div class="side-row"><span class="lbl">单位</span><span>{{ info.unit || '-' }}</span></div>
          <div class="side-row"><span class="lbl">价格</span><span v-if="info.current_price != null">¥ {{ info.current_price }}</span><span v-else>-</span></div>
          <div class="side-row"><span class="lbl">税率</span><span>{{ info.tax_rate || '-' }}</span></div>
          <div class="side-row"><span class="lbl">状态</span><el-tag size="small" :type="info.status === 'active' ? 'success' : 'info'">{{ info.status || '-' }}</el-tag></div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-tabs v-model="tab">
          <el-tab-pane label="销售订单" name="orders" />
          <el-tab-pane label="库存" name="inventory" />
          <el-tab-pane label="报台明细" name="surgery" />
        </el-tabs>
        <div v-if="tab === 'orders'">
          <el-table :data="orderRows" v-loading="loading.orders" border stripe size="small" max-height="480">
            <el-table-column prop="code" label="订单号" width="180" />
            <el-table-column prop="dealer_name" label="经销商" />
            <el-table-column prop="qty" label="数量" width="100" align="right" />
            <el-table-column prop="sub_total" label="金额" width="140" align="right">
              <template #default="{ row }">¥ {{ Number(row.sub_total || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="下单时间" width="170"><template #default="{ row }">{{ formatDateTime(row.created_at) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="!loading.orders && orderRows.length === 0" description="暂无订单" />
        </div>
        <div v-else-if="tab === 'inventory'">
          <el-table :data="invRows" v-loading="loading.inventory" border stripe size="small" max-height="480">
            <el-table-column prop="dealer_name" label="经销商" />
            <el-table-column prop="batch_no" label="批次" width="140" />
            <el-table-column prop="qty" label="数量" width="100" align="right" />
            <el-table-column prop="stock_status" label="状态" width="100" />
            <el-table-column label="生产日期" width="120"><template #default="{ row }">{{ formatDate(row.prod_date) }}</template></el-table-column>
            <el-table-column label="有效期" width="120"><template #default="{ row }">{{ formatDate(row.exp_date) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="!loading.inventory && invRows.length === 0" description="暂无库存" />
        </div>
        <div v-else>
          <el-table :data="surgeryRows" v-loading="loading.surgery" border stripe size="small" max-height="480">
            <el-table-column prop="code" label="报台号" width="180" />
            <el-table-column prop="doctor_name" label="医生" width="120" />
            <el-table-column prop="qty" label="数量" width="100" align="right" />
            <el-table-column label="手术日" width="120"><template #default="{ row }">{{ formatDate(row.surgery_date) }}</template></el-table-column>
            <el-table-column prop="patient_info" label="患者" />
          </el-table>
          <el-empty v-if="!loading.surgery && surgeryRows.length === 0" description="暂无报台" />
        </div>
      </el-col>
    </el-row>
    <el-empty v-else-if="!loading.info" description="未找到该产品" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { formatDateTime, formatDate } from '@/utils/format'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'

const route = useRoute()
const productId = () => route.params.id
const info = ref(null)
const tab = ref('orders')
const orderRows = ref([])
const invRows = ref([])
const surgeryRows = ref([])
const loading = reactive({ info: false, orders: false, inventory: false, surgery: false })

async function loadInfo() {
  loading.info = true
  try {
    const res = await request({ url: `/api/products/${productId()}`, method: 'get' })
    info.value = res?.data || null
  } catch (e) { info.value = null } finally { loading.info = false }
}
async function loadOrders() {
  loading.orders = true
  try {
    const res = await request({ url: '/api/reports/order-trace', method: 'get', params: { limit: 50 } })
    orderRows.value = res?.data || []
  } catch (e) { orderRows.value = [] } finally { loading.orders = false }
}
async function loadInv() {
  loading.inventory = true
  try {
    const res = await request({ url: '/api/reports/inventory-turnover', method: 'get', params: { limit: 50 } })
    invRows.value = (res?.data || []).filter(r => String(r.productId) === String(productId()))
  } catch (e) { invRows.value = [] } finally { loading.inventory = false }
}
async function loadSurgery() {
  loading.surgery = true
  try {
    // 报台明细通过 reports 端点拿不到产品维度，先用全量
    const res = await request({ url: '/api/reports/surgery-stats', method: 'get', params: { limit: 50 } })
    surgeryRows.value = (res?.data || []).filter(r => String(r.hospitalId) !== '')
  } catch (e) { surgeryRows.value = [] } finally { loading.surgery = false }
}
watch(tab, (v) => {
  if (v === 'orders') loadOrders()
  else if (v === 'inventory') loadInv()
  else if (v === 'surgery') loadSurgery()
})
onMounted(() => { loadInfo(); loadOrders() })
</script>

<style scoped>
.pd-head { margin-bottom: 12px; }
.pd-title { font-size: 18px; font-weight: 600; margin-left: 8px; }
.pd-side .side-row { display: flex; padding: 6px 0; font-size: 14px; }
.pd-side .lbl { width: 100px; color: var(--dms-text-4); }
</style>
