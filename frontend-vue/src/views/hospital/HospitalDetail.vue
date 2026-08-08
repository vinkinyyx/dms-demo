<template>
  <div>
    <el-card shadow="never" class="hd-head">
      <el-button :icon="ArrowLeft" link @click="$router.back()">返回</el-button>
      <span v-if="info" class="hd-title">{{ info.name }}（{{ info.code }}）</span>
      <span v-else class="hd-title">医院/终端详情</span>
    </el-card>

    <el-row :gutter="12" v-if="info">
      <el-col :span="8">
        <el-card shadow="never" class="hd-side">
          <div class="side-row"><span class="lbl">编码</span><span>{{ info.code }}</span></div>
          <div class="side-row"><span class="lbl">名称</span><span>{{ info.name }}</span></div>
          <div class="side-row"><span class="lbl">级别</span><span>{{ info.level || '-' }}</span></div>
          <div class="side-row"><span class="lbl">省份</span><span>{{ info.province || '-' }}</span></div>
          <div class="side-row"><span class="lbl">城市</span><span>{{ info.city || '-' }}</span></div>
          <div class="side-row"><span class="lbl">地址</span><span>{{ info.address || '-' }}</span></div>
          <div class="side-row"><span class="lbl">联系人</span><span>{{ info.contactName || info.contact_name || '-' }}</span></div>
          <div class="side-row"><span class="lbl">电话</span><span>{{ info.contactPhone || info.contact_phone || '-' }}</span></div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-tabs v-model="tab">
          <el-tab-pane label="手术报台" name="surgery" />
          <el-tab-pane label="关联订单" name="orders" />
        </el-tabs>
        <div v-if="tab === 'surgery'">
          <el-table :data="surgeryRows" v-loading="loading.surgery" border stripe size="small" max-height="480">
            <el-table-column prop="code" label="报台号" width="180" />
            <el-table-column prop="doctor_name" label="医生" width="120" />
            <el-table-column prop="patient_info" label="患者" />
            <el-table-column prop="surgery_date" label="手术日" width="120" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
          <el-empty v-if="!loading.surgery && surgeryRows.length === 0" description="暂无报台" />
        </div>
        <div v-else>
          <el-table :data="orderRows" v-loading="loading.orders" border stripe size="small" max-height="480">
            <el-table-column prop="orderCode" label="订单号" width="180" />
            <el-table-column prop="dealerName" label="经销商" />
            <el-table-column prop="approvalStatus" label="状态" width="100" />
            <el-table-column prop="totalAmount" label="金额" width="140" align="right">
              <template #default="{ row }">¥ {{ Number(row.totalAmount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="orderDate" label="下单日" width="170" />
          </el-table>
          <el-empty v-if="!loading.orders && orderRows.length === 0" description="暂无订单" />
        </div>
      </el-col>
    </el-row>
    <el-empty v-else-if="!loading.info" description="未找到该医院" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'

const route = useRoute()
const hospitalId = () => route.params.id
const info = ref(null)
const tab = ref('surgery')
const surgeryRows = ref([])
const orderRows = ref([])
const loading = reactive({ info: false, surgery: false, orders: false })

async function loadInfo() {
  loading.info = true
  try {
    const res = await request({ url: `/api/hospitals/${hospitalId()}`, method: 'get' })
    info.value = res?.data || null
  } catch (e) { info.value = null } finally { loading.info = false }
}
async function loadSurgery() {
  loading.surgery = true
  try {
    const res = await request({ url: '/api/reports/surgery-stats', method: 'get', params: { hospitalId: hospitalId(), limit: 50 } })
    surgeryRows.value = res?.data || []
  } catch (e) { surgeryRows.value = [] } finally { loading.surgery = false }
}
async function loadOrders() {
  loading.orders = true
  try {
    const res = await request({ url: '/api/reports/order-trace', method: 'get', params: { hospitalId: hospitalId(), limit: 50 } }).catch(() => null)
    orderRows.value = res?.data || []
  } catch (e) { orderRows.value = [] } finally { loading.orders = false }
}
watch(tab, (v) => { if (v === 'surgery') loadSurgery(); else if (v === 'orders') loadOrders() })
onMounted(() => { loadInfo(); loadSurgery() })
</script>

<style scoped>
.hd-head { margin-bottom: 12px; }
.hd-title { font-size: 18px; font-weight: 600; margin-left: 8px; }
.hd-side .side-row { display: flex; padding: 6px 0; font-size: 14px; }
.hd-side .lbl { width: 100px; color: #909399; }
</style>
