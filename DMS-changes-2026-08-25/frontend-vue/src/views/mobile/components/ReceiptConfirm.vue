<template>
  <div class="receipt-confirm">
    <van-nav-bar title="收货确认" left-text="返回" @click-left="$emit('close')" />
    <van-loading v-if="loading" class="loading" />
    <template v-else-if="receipt">
      <van-cell-group inset>
        <van-cell title="收货单号" :value="receipt.code" />
        <van-cell title="供应商" :value="receipt.supplierName || '-'" />
        <van-cell title="状态" :value="statusText(receipt.status)" />
      </van-cell-group>
      <van-cell-group inset title="收货明细">
        <div v-for="(line, index) in lines" :key="line.id || index" class="line">
          <div class="line-title">{{ line.productName || line.productCode }}</div>
          <div class="line-meta">应收 {{ line.expectedQty }} {{ line.unit || '' }}</div>
          <van-field v-model="line.receivedQty" type="number" label="收货数量" placeholder="请输入" />
        </div>
      </van-cell-group>
      <div class="actions">
        <van-button type="primary" block :loading="submitting" @click="confirmPartial">确认部分收货</van-button>
        <van-button plain type="success" block :loading="submitting" @click="confirmFull">按订单量收货</van-button>
      </div>
    </template>
    <van-empty v-else description="未查到收货单" />
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { showSuccessToast, showFailToast } from 'vant'
import request from '@/utils/request'
import { statusText } from '@/utils/dict'
const props = defineProps({ id: { type: [Number, String], required: true } })
const emit = defineEmits(['close', 'done'])
const loading = ref(true)
const submitting = ref(false)
const receipt = ref(null)
const lines = ref([])
onMounted(async () => {
  try {
    const { data } = await request({ url: '/api/receipts/' + props.id, method: 'get' })
    receipt.value = data
    lines.value = ((data && (data.lines || data.receiptLines)) || []).map(line => ({ ...line, receivedQty: line.expectedQty ?? line.qty ?? 0 }))
  } catch { receipt.value = null }
  finally { loading.value = false }
})
async function confirmPartial() {
  if (submitting.value) return
  submitting.value = true
  try {
    await request({
      url: '/api/receipts/' + props.id + '/confirm',
      method: 'post',
      data: { lines: lines.value.map(line => ({ id: line.id, receivedQty: Number(line.receivedQty) })) }
    })
    showSuccessToast('提交成功')
    emit('done')
  } catch (e) {
    showFailToast((e && e.message) || '提交失败')
  } finally { submitting.value = false }
}
async function confirmFull() {
  if (submitting.value) return
  submitting.value = true
  try {
    await request({ url: '/api/receipts/' + props.id + '/confirm-full', method: 'post' })
    showSuccessToast('收货成功')
    emit('done')
  } catch (e) {
    showFailToast((e && e.message) || '收货失败')
  } finally { submitting.value = false }
}
</script>
<style scoped>
.loading { display: block; margin: 60px auto; }
.line { padding: 8px 16px; border-bottom: 1px solid #f2f3f5; }
.line-title { font-weight: 500; margin-bottom: 4px; }
.line-meta { color: #969799; font-size: 12px; margin-bottom: 8px; }
.actions { padding: 16px; display: grid; gap: 10px; }
</style>
