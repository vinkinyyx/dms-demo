<template>
  <div>
    <van-nav-bar title="报台详情" left-arrow @click-left="$router.back()" />
    <div v-if="loading" class="loading"><van-loading type="spinner" /></div>
    <div v-else-if="!data" class="empty"><van-empty description="报台不存在" /></div>
    <div v-else class="detail-body">
      <div class="status-bar st-completed">
        <div class="st-text">{{ statusText(data.status) }}</div>
        <div class="st-code">{{ data.code }}</div>
      </div>

      <van-cell-group inset title="基本信息" style="margin-top:10px">
        <van-cell title="单号" :value="data.code" />
        <van-cell title="经销商" :value="data.dealerName || '-'" />
        <van-cell title="医院" :value="data.terminalName || '-'" />
        <van-cell title="仓库" :value="data.warehouseName || '-'" />
        <van-cell title="手术日期" :value="data.surgeryDate || '-'" />
        <van-cell title="患者" :value="data.patientInfo || '-'" />
        <van-cell title="主刀医生" :value="data.doctorName || '-'" />
        <van-cell v-if="data.remark" title="备注" :value="data.remark" />
        <van-cell title="创建时间" :value="fmt(data.createdAt)" />
      </van-cell-group>

      <van-cell-group inset title="产品明细" style="margin-top:10px">
        <div v-if="lines.length">
          <van-cell
            v-for="(l, idx) in lines" :key="idx"
            :title="l.productName + (l.spec ? ' / ' + l.spec : '')"
            :label="(l.isSerialManaged ? '序列号：' : '批号：') + (l.serialNo || l.batchNo || '-')"
            :value="'× ' + (l.qty || 0)"
          />
        </div>
        <van-empty v-else description="暂无明细" />
      </van-cell-group>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getResource } from '@/api/crud'
import { statusText } from '@/utils/dict'

const route = useRoute()
const id = route.params.id
const loading = ref(true)
const data = ref(null)
const lines = computed(() => (data.value?.lines || []).map(l => ({ ...l, isSerialManaged: !!l.serialNo })))

function fmt(v) { return v ? String(v).substring(0, 19).replace('T', ' ') : '-' }

onMounted(async () => {
  try {
    const res = await getResource('/api/surgery-reports', id)
    data.value = res.data || null
  } catch (e) { data.value = null } finally { loading.value = false }
})
</script>

<style scoped>
.loading, .empty { padding: 40px 16px; text-align: center; }
.detail-body { padding-bottom: 20px; }
.status-bar { padding: 16px 20px; color: #fff; }
.status-bar .st-text { font-size: 18px; font-weight: 600; }
.status-bar .st-code { font-size: 13px; opacity: .9; margin-top: 4px; }
.st-completed { background: #67C23A; }
</style>