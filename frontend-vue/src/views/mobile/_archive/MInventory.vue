<template>
  <div>
    <van-nav-bar title="库存与追溯" />
    <van-search v-model="serialNo" placeholder="输入序列号追溯" @search="doTrace">
      <template #action><div @click="doTrace">追溯</div></template>
    </van-search>

    <div v-if="traceResult">
      <van-cell-group inset title="追溯结果" style="margin-top:10px">
        <van-cell title="序列号" :value="serialNo" />
        <van-cell title="当前库存状态" :value="traceResult.currentStock ? '在库' : '不在库'" />
      </van-cell-group>
      <van-steps direction="vertical" :active="0" v-if="events.length">
        <van-step v-for="(e, i) in events" :key="i">
          <h4>{{ eventLabel(e.eventType || e.type) }}</h4>
          <p>{{ (e.atTime || e.time || '').substring(0, 19).replace('T', ' ') }} · {{ e.warehouseName || e.remark || '' }}</p>
        </van-step>
      </van-steps>
    </div>

    <div class="sec-title">库存清单</div>
    <van-cell-group inset>
      <van-cell v-for="it in list" :key="it.id" :title="it.productName" :label="'批次:' + (it.batchNo || '-') + ' 序列:' + (it.serialNo || '-')"
        :value="'数量 ' + it.qty" />
      <van-empty v-if="!list.length" description="暂无库存" />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showToast } from 'vant'
import { listResource } from '@/api/crud'
import { traceBySerial } from '@/api/mobile'

const serialNo = ref('')
const traceResult = ref(null)
const events = ref([])
const list = ref([])

const EVENT_LABELS = { RECEIPT: '收货入库', SALES_OUT: '销售出库', ADJUST: '库存调整', MOVE: '库存移动', RMA: '退货' }
function eventLabel(t) { return EVENT_LABELS[t] || t || '事件' }

async function doTrace() {
  if (!serialNo.value.trim()) { showToast('请输入序列号'); return }
  try {
    const d = (await traceBySerial(serialNo.value.trim())).data || {}
    traceResult.value = d
    events.value = d.events || []
  } catch (e) { traceResult.value = null }
}
async function loadList() {
  try {
    const r = await listResource('/api/inventory', { page: 1, size: 30 })
    const d = r.data
    list.value = Array.isArray(d) ? d : (d.list || d.records || [])
  } catch (e) { /* ignore */ }
}
loadList()
</script>

<style scoped>
.sec-title { font-size: 15px; font-weight: 600; margin: 16px 16px 8px; }
</style>
