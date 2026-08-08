<template>
  <div class="m-receipt">
    <van-nav-bar title="收货入库"></van-nav-bar>

    <!-- 步骤1：选择采购订单 -->
    <div v-if="step === 'selectPo'">
      <div class="sec-title">选择要收货的采购订单</div>
      <van-list v-model:loading="poLoading" :finished="poFinished" finished-text="没有更多了" @load="loadPoList">
        <van-cell-group inset v-for="po in poList" :key="po.id" style="margin-top:10px">
          <van-cell :title="po.code" :label="'供应商：' + (po.supplierName || '-') + ' · 金额：¥' + (po.finalAmount || 0)"
            is-link @click="selectPo(po)">
            <template #value>
              <van-tag :type="tagType(po.status)">{{ statusText(po.status) }}</van-tag>
            </template>
          </van-cell>
        </van-cell-group>
      </van-list>
      <van-empty v-if="poFinished && !poList.length" description="暂无待收货采购订单"></van-empty>
    </div>

    <!-- 步骤2：收货操作 -->
    <div v-else-if="step === 'receipt'">
      <van-nav-bar title="收货操作" left-arrow @click-left="step = 'selectPo'"></van-nav-bar>

      <!-- 扫码输入区 -->
      <van-search v-model="scanInput" placeholder="扫描/输入序列号" show-action @search="onScan">
        <template #action>
          <van-button size="small" type="primary" @click="onScan">收货</van-button>
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
            <van-icon name="cross" @click="removeScanned(item.serialNo)"></van-icon>
          </template>
        </van-cell>
        <van-cell title="合计" :value="scannedItems.length + ' 件'"></van-cell>
      </van-cell-group>

      <!-- 当前收货单 -->
      <div class="sec-title">待收货清单（{{ selectedPo?.code }}）</div>
      <van-cell-group inset v-if="currentReceipt">
        <van-cell :title="currentReceipt.code" :label="'仓库：' + (currentReceipt.warehouseName || '-')">
          <template #value>
            <van-tag :type="statusTagType(currentReceipt.status)" size="small">{{ statusText(currentReceipt.status) }}</van-tag>
          </template>
        </van-cell>
        <van-cell title="待收货明细"></van-cell>
        <van-collapse v-model="activeDetail">
          <van-collapse-item v-for="line in currentReceipt.lines" :key="line.id" :name="line.id">
            <template #title>
              <div class="line-title">
                <span class="line-product">{{ line.productName || '产品' + line.productId }}</span>
                <van-tag size="small" type="primary">{{ line.remainingQty }}待收</van-tag>
              </div>
            </template>
            <div class="line-detail">
              <van-row>
                <van-col span="12">应收：{{ line.qty }}</van-col>
                <van-col span="12">已收：{{ line.receivedQty || 0 }}</van-col>
              </van-row>
              <van-row style="margin-top:8px">
                <van-col span="12">批次号：{{ line.batchNo || '-' }}</van-col>
                <van-col span="12">本次收货：{{ getLineReceiveQty(line) }}</van-col>
              </van-row>
              <van-stepper v-model="line.receiveQty" :min="0" :max="line.remainingQty" integer style="margin-top:8px"></van-stepper>
            </div>
          </van-collapse-item>
        </van-collapse>
      </van-cell-group>
      <van-empty v-else description="该采购订单暂无待收货单据"></van-empty>

      <!-- 操作按钮 -->
      <div class="action-bar" v-if="currentReceipt">
        <van-button type="danger" plain @click="onCancel">取消收货</van-button>
        <van-button type="primary" @click="submitReceive" :loading="submitting">确认收货</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { listResource, getDetail, actionResource } from '@/api/crud'
import { statusText, statusTagType } from '@/utils/dict'

const step = ref('selectPo')
const poList = ref([])
const poLoading = ref(false)
const poFinished = ref(false)
let poPage = 1

const selectedPo = ref(null)
const scanInput = ref('')
const scanResult = ref(null)
const scannedItems = ref([])
const currentReceipt = ref(null)
const activeDetail = ref([])
const submitting = ref(false)

function tagType(s) {
  const u = String(s || '').toUpperCase()
  if (['APPROVED', 'COMPLETED'].includes(u)) return 'success'
  if (['SUBMITTED', 'DRAFT'].includes(u)) return 'warning'
  if (['REJECTED', 'CANCELLED'].includes(u)) return 'danger'
  return 'primary'
}

async function loadPoList() {
  poLoading.value = true
  try {
    const r = await listResource('/api/purchase-orders', { page: poPage, size: 20, status: 'APPROVED' })
    const d = r.data
    const rows = Array.isArray(d) ? d : (d.list || d.records || [])
    poList.value.push(...rows)
    poPage++
    if (rows.length < 20) poFinished.value = true
  } catch (e) { poFinished.value = true } finally { poLoading.value = false }
}

async function selectPo(po) {
  selectedPo.value = po
  // 查找该采购订单关联的待收货单据
  try {
    const r = await listResource('/api/receipts', { page: 1, size: 10, sourcePoCode: po.code })
    const d = r.data
    const list = Array.isArray(d) ? d : (d.list || d.records || [])
    const valid = list.filter(item => ['DRAFT', 'PARTIAL_RECEIVED'].includes(item.status))
    if (valid.length) {
      await loadReceipt(valid[0])
      step.value = 'receipt'
    } else {
      showToast('该采购订单暂无待收货单据')
    }
  } catch (e) {
    showToast.fail('加载收货单失败')
  }
}

async function loadReceipt(r) {
  try {
    const res = await getDetail('/api/receipts', r.id)
    const d = res.data || {}
    currentReceipt.value = {
      ...d,
      lines: (d.lines || d.items || []).map(l => ({
        ...l,
        receiveQty: 0,
        remainingQty: (l.qty || 0) - (l.receivedQty || 0),
        serialNos: []
      }))
    }
  } catch (e) {
    showToast.fail('加载收货单详情失败')
  }
}

async function onScan() {
  const serialNo = scanInput.value.trim()
  if (!serialNo) {
    showToast('请输入序列号')
    return
  }

  if (!currentReceipt.value) {
    showToast.fail('请先选择收货单')
    return
  }

  // 检查是否已扫描
  if (scannedItems.value.find(item => item.serialNo === serialNo)) {
    showToast('序列号已扫描')
    scanInput.value = ''
    return
  }

  // 查找匹配的明细行
  const matchedLine = currentReceipt.value.lines.find(l => l.remainingQty > 0 && (l.receiveQty || 0) < l.remainingQty)
  if (!matchedLine) {
    scanResult.value = { success: false, message: '无待收货明细' }
    scanInput.value = ''
    return
  }

  // 添加到已扫描列表
  scannedItems.value.push({
    serialNo,
    productId: matchedLine.productId,
    productName: matchedLine.productName || '产品' + matchedLine.productId,
    qty: 1
  })

  // 增加对应行的收货数量
  matchedLine.receiveQty = (matchedLine.receiveQty || 0) + 1
  if (!matchedLine.serialNos) matchedLine.serialNos = []
  matchedLine.serialNos.push(serialNo)

  scanResult.value = { success: true, message: `已扫描 ${serialNo}` }
  scanInput.value = ''
}

function removeScanned(serialNo) {
  const index = scannedItems.value.findIndex(item => item.serialNo === serialNo)
  if (index > -1) {
    const item = scannedItems.value[index]
    const line = currentReceipt.value?.lines.find(l => l.productId === item.productId)
    if (line) {
      line.receiveQty = Math.max(0, (line.receiveQty || 0) - 1)
      if (line.serialNos) {
        const si = line.serialNos.indexOf(serialNo)
        if (si > -1) line.serialNos.splice(si, 1)
      }
    }
    scannedItems.value.splice(index, 1)
  }
}

function getLineReceiveQty(line) {
  return line.receiveQty || 0
}

async function submitReceive() {
  const validLines = currentReceipt.value?.lines?.filter(l => (l.receiveQty || 0) > 0) || []
  if (!validLines.length) {
    showToast('请先扫描序列号或填写收货数量')
    return
  }

  try {
    await showConfirmDialog({ title: '确认收货', message: `确认收货 ${validLines.reduce((s, l) => s + (l.receiveQty || 0), 0)} 件商品？` })
  } catch {
    return
  }

  submitting.value = true
  try {
    const payload = {
      lines: validLines.map(l => ({
        productId: l.productId,
        batchNo: l.batchNo,
        qty: l.receiveQty,
        serialNos: l.serialNos
      }))
    }
    await actionResource('/api/receipts', currentReceipt.value.id, '/execute', 'post', payload)
    showToast.success('收货成功')
    scannedItems.value = []
    scanResult.value = null
    currentReceipt.value = null
    step.value = 'selectPo'
    poList.value = []
    poPage = 1
    poFinished.value = false
    loadPoList()
  } catch (e) {
    showToast.fail('收货失败')
  } finally {
    submitting.value = false
  }
}

async function onCancel() {
  try {
    await showConfirmDialog({ title: '取消收货', message: '确认取消整单收货？', confirmButtonColor: '#ee0a24' })
  } catch {
    return
  }
  try {
    await actionResource('/api/receipts', currentReceipt.value.id, '/cancel-draft', 'post')
    showToast.success('已取消')
    currentReceipt.value = null
    step.value = 'selectPo'
    poList.value = []
    poPage = 1
    poFinished.value = false
    loadPoList()
  } catch (e) {
    showToast.fail('取消失败')
  }
}
</script>

<style scoped>
.m-receipt { padding-bottom: 80px; }
.sec-title { font-size: 15px; font-weight: 600; margin: 16px 16px 8px; color: #323233; }
.line-title { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.line-product { font-weight: 500; }
.line-detail { font-size: 13px; color: #646566; }
.action-bar { position: fixed; bottom: 50px; left: 0; right: 0; padding: 8px 16px; background: #fff; display: flex; gap: 8px; border-top: 1px solid #ebedf0; z-index: 10; }
.action-bar .van-button { flex: 1; }
</style>
