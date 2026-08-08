<template>
  <div>
    <el-card shadow="never" class="od-head">
      <el-button :icon="ArrowLeft" link @click="$router.back()">返回</el-button>
      <span v-if="info" class="od-title">订单 {{ info.code }} 详情</span>
      <span v-else class="od-title">订单详情</span>
    </el-card>

    <el-row :gutter="12" v-if="info">
      <el-col :span="8">
        <el-card shadow="never" class="od-side">
          <div class="side-row"><span class="lbl">订单号</span><span>{{ info.code }}</span></div>
          <div class="side-row"><span class="lbl">类型</span><el-tag size="small">{{ info.orderType || info.order_type }}</el-tag></div>
          <div class="side-row"><span class="lbl">状态</span><el-tag size="small" :type="statusType(info.status)">{{ statusLabel(info.status) }}</el-tag></div>
          <div class="side-row"><span class="lbl">经销商</span><span>{{ info.dealerName || info.dealer_name || '-' }}</span></div>
          <div class="side-row"><span class="lbl">金额</span><span>¥ {{ Number(info.amount_incl_tax || info.totalAmount || 0).toFixed(2) }}</span></div>
          <div class="side-row"><span class="lbl">下单时间</span><span>{{ formatDateTime(info.created_at || info.orderDate) }}</span></div>
          <div class="side-row"><span class="lbl">审批时间</span><span>{{ formatDateTime(info.approved_at || info.approvedAt) }}</span></div>
          <div class="side-row"><span class="lbl">发货时间</span><span>{{ formatDateTime(info.shipped_at || info.shippedAt) }}</span></div>
          <div class="side-row"><span class="lbl">收货时间</span><span>{{ formatDateTime(info.received_at || info.receivedAt) }}</span></div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-tabs v-model="tab">
          <el-tab-pane label="订单行" name="lines" />
          <el-tab-pane label="状态历史" name="history" />
        </el-tabs>
        <div v-if="tab === 'lines'">
          <el-table :data="lineRows" v-loading="loading.lines" border stripe size="small">
            <el-table-column prop="productCode" label="产品编码" width="140" />
            <el-table-column prop="productName" label="产品名" />
            <el-table-column prop="qty" label="数量" width="100" align="right" />
            <el-table-column prop="unitPrice" label="单价" width="120" align="right" />
            <el-table-column prop="subTotal" label="小计" width="140" align="right" />
          </el-table>
          <el-empty v-if="!loading.lines && lineRows.length === 0" description="暂无订单行" />
        </div>
        <div v-else>
          <el-timeline>
            <el-timeline-item v-for="h in historyRows" :key="h.id || h.at_time" :timestamp="formatDateTime(h.at_time)" :type="historyType(h.to_status)">
              {{ h.from_status || '初始' }} → <b>{{ h.to_status }}</b>
            </el-timeline-item>
            <el-empty v-if="!loading.history && historyRows.length === 0" description="暂无状态变更" />
          </el-timeline>
        </div>
      </el-col>
    </el-row>
    <el-empty v-else-if="!loading.info" description="未找到该订单" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const orderId = () => route.params.id
const info = ref(null)
const tab = ref('lines')
const lineRows = ref([])
const historyRows = ref([])
const loading = reactive({ info: false, lines: false, history: false })

async function loadInfo() {
  loading.info = true
  try {
    const res = await request({ url: `/api/orders/${orderId()}`, method: 'get' })
    info.value = res?.data || null
  } catch (e) { info.value = null } finally { loading.info = false }
}
async function loadLines() {
  loading.lines = true
  try {
    const data = info.value
    lineRows.value = data?.lines || data?.orderLines || []
  } catch (e) { lineRows.value = [] } finally { loading.lines = false }
}
async function loadHistory() {
  loading.history = true
  try {
    const res = await request({ url: `/api/orders/${orderId()}/status-history`, method: 'get' }).catch(() => null)
    historyRows.value = res?.data || []
  } catch (e) { historyRows.value = [] } finally { loading.history = false }
}
watch(tab, (v) => { if (v === 'lines') loadLines(); else if (v === 'history') loadHistory() })
function statusLabel(s) { return { DRAFT: '草稿', SUBMITTED: '已提交', APPROVED: '已审批', SHIPPING: '发货中', COMPLETED: '已完成', REJECTED: '已拒绝', CANCELLED: '已取消' }[s] || s || '-' }
function statusType(s) { return { APPROVED: 'success', COMPLETED: 'success', SHIPPING: 'warning', DRAFT: 'info', SUBMITTED: 'primary', REJECTED: 'danger', CANCELLED: 'danger' }[s] || '' }
function historyType(s) { return statusType(s) || 'primary' }
onMounted(() => { loadInfo(); loadLines() })
</script>

<style scoped>
.od-head { margin-bottom: 12px; }
.od-title { font-size: 18px; font-weight: 600; margin-left: 8px; }
.od-side .side-row { display: flex; padding: 6px 0; font-size: 14px; }
.od-side .lbl { width: 100px; color: #909399; }
</style>
