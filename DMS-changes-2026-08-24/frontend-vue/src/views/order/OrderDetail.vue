<template>
  <div class="area-page order-detail-page"><div class="area-scroll">
    <el-card shadow="never" class="od-head">
      <div class="head-left">
        <el-button :icon="ArrowLeft" link @click="goOrders">返回</el-button>
        <span v-if="info" class="od-title">销售订单 {{ info.code }}</span>
      </div>
      <div class="head-right">
        <el-button v-if="canSimulate" type="primary" :loading="acting" @click="simulateShip">生成销售出库</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" v-loading="loading.info">
      <el-col :span="18">
        <el-card shadow="never">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="订单号">{{ info?.code }}</el-descriptions-item>
            <el-descriptions-item label="经销商">{{ info?.dealerName }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag size="small" :type="statusTagType(info?.status)">{{ statusText(info?.status) }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="订单类型">{{ enumText(info?.orderType, 'orderType') }}</el-descriptions-item>
            <el-descriptions-item label="期望日期">{{ info?.expectedDate }}</el-descriptions-item>
            <el-descriptions-item label="最终金额">¥{{ Number(info?.finalAmount || 0).toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="3">{{ info?.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="side-row"><span class="lbl">创建时间</span><span>{{ formatDateTime(info?.createdAt) }}</span></div>
          <div class="side-row"><span class="lbl">提交时间</span><span>{{ formatDateTime(info?.submittedAt) }}</span></div>
          <div class="side-row"><span class="lbl">审批时间</span><span>{{ formatDateTime(info?.approvedAt) }}</span></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="lines-card order-lines-card">
      <template #header>订单明细</template>
      <el-table :data="lineTree" border size="small" row-key="id" default-expand-all :tree-props="treeProps">
        <el-table-column prop="seq" label="行号" width="70" align="center" />
        <el-table-column label="产品" min-width="240">
          <template #default="{ row }">
            {{ row.productCode }} {{ row.productName }}
            <el-tag v-if="row.lineLevel === 'PARENT'" size="small" type="warning" style="margin-left:6px">BOM母件</el-tag>
            <el-tag v-if="row.isGift" size="small" type="danger" style="margin-left:6px">赠品</el-tag>
            <el-tag v-for="t in promoNames(row)" :key="t" size="small" type="success" style="margin-left:6px">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productSpec" label="规格" width="140" show-overflow-tooltip />
        <el-table-column label="单位" width="70" align="center"><template #default="{ row }">{{ row.unit || '-' }}</template></el-table-column>
        <el-table-column prop="qty" label="数量" width="90" align="right" />
        <el-table-column label="含税单价" width="115" align="right"><template #default="{ row }">¥{{ money(row.standardPriceInclTax || row.unitPrice || 0) }}</template></el-table-column>
        <el-table-column label="标准金额" width="120" align="right"><template #default="{ row }">¥{{ money(row.standardAmount) }}</template></el-table-column>
        <el-table-column label="行折扣" width="100" align="right"><template #default="{ row }">¥{{ money(row.lineDiscountAmount) }}</template></el-table-column>
        <el-table-column label="促销折扣" width="105" align="right"><template #default="{ row }">¥{{ money(row.promoDiscountAmount) }}</template></el-table-column>
        <el-table-column label="整单折扣" width="105" align="right"><template #default="{ row }">¥{{ money(row.headerDiscountAmount) }}</template></el-table-column>
        <el-table-column label="最终金额" width="125" align="right"><template #default="{ row }"><b>¥{{ money(row.finalAmount) }}</b></template></el-table-column>
      </el-table>
      <div v-if="lineTree.length" class="lines-summary">
        <span>明细行数：{{ lineTree.length }}</span>
        <span>订单总金额：<b class="amount-text">¥{{ Number(info?.finalAmount || 0).toFixed(2) }}</b></span>
      </div>
    </el-card>

    <el-card shadow="never" class="logs-card">
      <template #header>操作日志</template>
      <el-timeline v-if="logs.length">
        <el-timeline-item v-for="h in logs" :key="h.id || h.atTime" :timestamp="formatDateTime(h.atTime || h.at_time)">
          <el-tag size="small">{{ h.username || h.operator || '系统' }}</el-tag>
          <b>{{ actionText(h.action) }}</b>
          <div class="muted">{{ enhanceChanges(h.changes || h.remark || '', info) }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无操作日志" />
    </el-card>
    </div>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getOperationLogs } from '@/api/crud'
import { formatDateTime } from '@/utils/format'
import { statusText, statusTagType, actionText, enhanceChanges, ENUMS } from '@/utils/dict'

function enumText(v, key) {
  if (v == null || v === '') return '-'
  const list = ENUMS[key]
  if (list && list.length) {
    const hit = list.find(o => String(o.value) === String(v))
    if (hit) return hit.label
  }
  return statusText(v)
}

const route = useRoute()
const router = useRouter()
const orderId = () => route.params.id
const info = ref(null)
const logs = ref([])
const acting = ref(false)
const loading = reactive({ info: false })
const treeProps = { children: 'children' }
const lineTree = computed(() => {
  const lines = info.value?.lines || []
  return lines.filter(l => !l.bomParentLineId).map(l => ({ ...l, children: lines.filter(c => String(c.bomParentLineId) === String(l.id)) }))
})
const canSimulate = computed(() => ['APPROVED', 'PARTIAL_OUTBOUND'].includes(info.value?.status))
function money(v) { return Number(v || 0).toFixed(2) }
function goOrders() { router.push('/m/orders') }
function promoNames(row) {
  const names = row.promoNames || row.priceSnapshot?.promoNames
  return names ? String(names).split(',').filter(Boolean) : []
}
async function loadInfo() {
  loading.info = true
  try {
    const res = await request({ url: `/api/sales-orders/${orderId()}`, method: 'get' })
    info.value = res?.data || null
    const lr = await getOperationLogs('sales_order', orderId(), 'salesOrder').catch(() => null)
    logs.value = Array.isArray(lr?.data) ? lr.data : []
  } finally { loading.info = false }
}
async function simulateShip() {
  await ElMessageBox.confirm('确认生成销售出库？', '提示', { type: 'warning' })
  acting.value = true
  try {
    await request({ url: `/api/sales-orders/${orderId()}/simulate-ship`, method: 'post' })
    ElMessage.success('销售出库草稿已生成')
    await loadInfo()
  } finally { acting.value = false }
}
onMounted(loadInfo)
</script>
<style scoped>
.order-detail-page .area-scroll{padding:0;display:flex;flex-direction:column;gap:12px}.od-head{flex:0 0 auto}.od-head :deep(.el-card__body){display:flex;justify-content:space-between;align-items:center}.head-left{display:flex;gap:10px;align-items:center}.od-title{font-size:18px;font-weight:600}.side-row{display:flex;justify-content:space-between;padding:7px 0}.side-row .lbl{color:var(--dms-text-4)}.lines-summary{display:flex;justify-content:flex-end;gap:32px;padding:12px 0 4px;color:#606266}.amount-text{color:#f56c6c;font-size:15px}.muted{color:var(--dms-text-4);font-size:12px;margin-top:4px}
</style>
