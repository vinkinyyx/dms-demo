<template>
  <div class="m-shipment">
    <van-nav-bar title="发货出库" />

    <!-- 步骤1：选择销售订单 -->
    <div v-if="step === 'selectSo'">
      <div class="sec-title">选择要发货的销售订单</div>
      <van-list v-model:loading="soLoading" :finished="soFinished" finished-text="没有更多了" @load="loadSoList">
        <van-cell-group inset v-for="so in soList" :key="so.id" style="margin-top:10px">
          <van-cell :title="so.code" :label="'经销商：' + (so.dealerName || '-') + ' · 金额：¥' + (so.finalAmount || 0)"
            is-link @click="selectSo(so)">
            <template #value>
              <van-tag :type="tagType(so.status)">{{ statusText(so.status) }}</van-tag>
            </template>
          </van-cell>
        </van-cell-group>
      </van-list>
      <van-empty v-if="soFinished && !soList.length" description="暂无待发货销售订单" />
    </div>

    <!-- 步骤2：发货操作 -->
    <div v-else-if="step === 'shipment'">
      <van-nav-bar title="发货操作" left-arrow @click-left="step = 'selectSo'" />

      <!-- 扫码输入区 -->
      <van-search v-model="scanInput" placeholder="扫描/输入序列号" show-action @search="onScan">
        <template #action>
          <van-button size="small" type="primary" @click="onScan">发货</van-button>
        </template>
      </van-search>

      <!-- 扫码结果提示 -->
      <van-notice-bar v-if="scanResult" :color="scanResult.success ? '#07c160' : '#ee0a24'" left-icon="info-o">
        {{ scanResult.message }}
      </van-notice-bar>

      <!-- 已扫描序列号列表 -->
      <van-cell-group inset v-if="scannedItems.length" title="已扫描序列号">
        <van-cell v-for="item in scannedItems" :key="item.serialNo" :title="item.serialNo"
          :label="item.productName + ' × ' + item.qty">
          <template #value>
            <van-icon name="cross" @click="removeScanned(item.serialNo)" />
          </template>
        </van-cell>
        <van-cell title="合计" :value="scannedItems.length + ' 件'" />
      </van-cell-group>

      <!-- 当前出库单 -->
      <div class="sec-title">待发货清单（{{ selectedSo?.code }}）</div>
      <van-cell-group inset v-if="currentOut">
        <van-cell :title="currentOut.code" :label="'仓库：' + (currentOut.warehouseName || '-')">
          <template #value>
            <van-tag :type="statusTagType(currentOut.status)" size="small">{{ statusText(currentOut.status) }}</van-tag>
          </template>
        </van-cell>
        <van-cell title="待发货明细" />
        <van-collapse v-model="activeDetail">
          <van-collapse-item v-for="line in currentOut.lines" :key="line.id" :name="line.id">
            <template #title>
              <div class="line-title">
                <span class="line-product">{{ line.productName || '产品' + line.productId }}</span>
                <van-tag size="small" type="primary">{{ line.remainingQty }}待发</van-tag>
              </div>
            </template>
            <div class="line-detail">
              <van-row>
                <van-col span="12">应发：{{ line.qty }}</van-col>
                <van-col span="12">已发：{{ line.shippedQty || 0 }}</van-col>
              </van-row>
              <van-row style="margin-top:8px">
                <van-col span="12">批次号：{{ line.batchNo || '-' }}</van-col>
                <van-col span="12">本次发货：{{ getLineShipQty(line) }}</van-col>
              </van-row>
              <van-stepper v-model="line.shipQty" :min="0" :max="line.remainingQty" integer style="margin-top:8px" />
            </div>
          </van-collapse-item>
        </van-collapse>
      </van-cell-group>
      <van-empty v-else description="该销售订单暂无待发货单据" />

      <!-- 操作按钮 -->
      <div class="action-bar" v-if="currentOut">
        <van-button type="danger" plain @click="onCancel">取消发货</van-button>
        <van-button type="primary" @click="submitShip" :loading="submitting">确认发货</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { listResource, getDetail, actionResource } from '@/api/crud'
import { statusText, statusTagType } from '@/utils/dict'

const step = ref('selectSo')
const soList = ref([])
const soLoading = ref(false)
const soFinished = ref(false)
let soPage = 1

const selectedSo = ref(null)
const scanInput = ref('')
const scanResult = ref(null)
const scannedItems = ref([])
const currentOut = ref(null)
const activeDetail = ref([])
const submitting = ref(false)

function tagType(s) {
  const u = String(s || '').toUpperCase()
  if (['APPROVED', 'COMPLETED'].includes(u)) return 'success'
  if (['SUBMITTED', 'DRAFT'].includes(u)) return 'warning'
  if (['REJECTED', 'CANCELLED'].includes(u)) return 'danger'
  return 'primary'
}

async function loadSoList() {
  soLoading.value = true
  try {
    const r = await listResource('/api/orders', { page: soPage, size: 20, status: 'APPROVED' })
    const d = r.data
    const rows = Array.isArray(d) ? d : (d.list || d.records || [])
    soList.value.push(...rows)
    soPage++
    if (rows.length < 20) soFinished.value = true
  } catch (e) { soFinished.value = true } finally { soLoading.value = false }
}

async function selectSo(so) {
  selectedSo.value = so
  try {
    // 尝试按来源订单号过滤销售出库单
    let list = []
    try {
      const r = await listResource('/api/sales-outs', { page: 1, size: 10, sourceOrderCode: so.code })
      const d = r.data
      list = Array.isArray(d) ? d : (d.list || d.records || [])
    } catch (e) {
      // 若不支持过滤参数，加载全部后前端过滤
      const r = await listResource('/api/sales-outs', { page: 1, size: 100 })
      const d = r.data
      const all = Array.isArray(d) ? d : (d.list || d.records || [])
      list = all.filter(item => item.sourceOrderCode === so.code)
    }
    const valid = list.filter(item => ['DRAFT', 'PARTIAL_SHIPPED'].includes(item.status))
    if (valid.length) {
      await loadOut(valid[0])
      step.value = 'shipment'
    } else {
      showToast('该销售订单暂无待发货单据')
    }
  } catch (e) {
    showToast.fail('加载发货单失败')
  }
}

async function loadOut(out) {
  try {
    const res = await getDetail('/api/sales-outs', out.id)
    const d = res.data || {}
    currentOut.value = {
      ...d,
      lines: (d.lines || d.items || []).map(l => ({
        ...l,
        shipQty: 0,
        remainingQty: (l.qty || 0) - (l.shippedQty || 0),
        serialNos: []
      }))
    }
  } catch (e) {
    showToast.fail('加载发货单详情失败')
  }
}

async function onScan() {
  const serialNo = scanInput.value.trim()
  if (!serialNo) {
    showToast('请输入序列号')
    return
  }

  if (!currentOut.value) {
    showToast.fail('请先选择发货单')
    return
  }

  if (scannedItems.value.find(item => item.serialNo === serialNo)) {
    showToast('序列号已扫描')
    scanInput.value = ''
    return
  }

  const matchedLine = currentOut.value.lines.find(l => l.remainingQty > 0 && (l.shipQty || 0) < l.remainingQty)
  if (!matchedLine) {
    scanResult.value = { success: false, message: '无待发货明细' }
    scanInput.value = ''
    return
  }

  scannedItems.value.push({
    serialNo,
    productId: matchedLine.productId,
    productName: matchedLine.productName || '产品' + matchedLine.productId,
    qty: 1
  })

  matchedLine.shipQty = (matchedLine.shipQty || 0) + 1
  if (!matchedLine.serialNos) matchedLine.serialNos = []
  matchedLine.serialNos.push(serialNo)

  scanResult.value = { success: true, message: `已扫描 ${serialNo}` }
  scanInput.value = ''
}

function removeScanned(serialNo) {
  const index = scannedItems.value.findIndex(item => item.serialNo === serialNo)
  if (index > -1) {
    const item = scannedItems.value[index]
    const line = currentOut.value?.lines.find(l => l.productId === item.productId)
    if (line) {
      line.shipQty = Math.max(0, (line.shipQty || 0) - 1)
      if (line.serialNos) {
        const si = line.serialNos.indexOf(serialNo)
        if (si > -1) line.serialNos.splice(si, 1)
      }
    }
    scannedItems.value.splice(index, 1)
  }
}

function getLineShipQty(line) {
  return line.shipQty || 0
}

async function submitShip() {
  const validLines = currentOut.value?.lines?.filter(l => (l.shipQty || 0) > 0) || []
  if (!validLines.length) {
    showToast('请先扫描序列号或填写发货数量')
    return
  }

  try {
    await showConfirmDialog({ title: '确认发货', message: `确认发货 ${validLines.reduce((s, l) => s + (l.shipQty || 0), 0)} 件商品？` })
  } catch {
    return
  }

  submitting.value = true
  try {
    const payload = {
      lines: validLines.map(l => ({
        productId: l.productId,
        batchNo: l.batchNo,
        qty: l.shipQty,
        serialNos: l.serialNos
      }))
    }
    await actionResource('/api/sales-outs', currentOut.value.id, '/execute', 'post', payload)
    showToast.success('发货成功')
    scannedItems.value = []
    scanResult.value = null
    currentOut.value = null
    step.value = 'selectSo'
    soList.value = []
    soPage = 1
    soFinished.value = false
    loadSoList()
  } catch (e) {
    showToast.fail('发货失败')
  } finally {
    submitting.value = false
  }
}

async function onCancel() {
  try {
    await showConfirmDialog({ title: '取消发货', message: '确认取消整单发货？', confirmButtonColor: '#ee0a24' })
  } catch {
    return
  }
  try {
    await actionResource('/api/sales-outs', currentOut.value.id, '/cancel-draft', 'post')
    showToast.success('已取消')
    currentOut.value = null
    step.value = 'selectSo'
    soList.value = []
    soPage = 1
    soFinished.value = false
    loadSoList()
  } catch (e) {
    showToast.fail('取消失败')
  }
}
</script>

<style scoped>
.m-shipment { padding-bottom: 80px; }
.sec-title { font-size: 15px; font-weight: 600; margin: 16px 16px 8px; color: #323233; }
.line-title { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.line-product { font-weight: 500; }
.line-detail { font-size: 13px; color: #646566; }
.action-bar { position: fixed; bottom: 50px; left: 0; right: 0; padding: 8px 16px; background: #fff; display: flex; gap: 8px; border-top: 1px solid #ebedf0; z-index: 10; }
.action-bar .van-button { flex: 1; }
</style>
