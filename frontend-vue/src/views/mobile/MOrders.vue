<template>
  <div>
    <van-nav-bar title="订单" />
    <van-tabs v-model:active="activeTab" sticky @change="onTabChange">
      <van-tab title="销售订单" name="sales">
        <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
          <van-cell-group inset v-for="o in salesList" :key="o.id" style="margin-top:10px">
            <van-cell :title="o.code" :label="'经销商：' + (o.dealerName || '-')" is-link @click="showDetail(o, 'sales')">
              <template #value>
                <div class="amt">¥ {{ o.finalAmount || 0 }}</div>
                <van-tag :type="tagType(o.status)">{{ statusText(o.status) }}</van-tag>
              </template>
            </van-cell>
          </van-cell-group>
        </van-list>
        <van-empty v-if="finished && !salesList.length" description="暂无销售订单" />
      </van-tab>
      <van-tab title="采购订单" name="purchase">
        <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
          <van-cell-group inset v-for="o in purchaseList" :key="o.id" style="margin-top:10px">
            <van-cell :title="o.code" :label="'供应商：' + (o.supplierName || '-')" is-link @click="showDetail(o, 'purchase')">
              <template #value>
                <div class="amt">¥ {{ o.finalAmount || 0 }}</div>
                <van-tag :type="tagType(o.status)">{{ statusText(o.status) }}</van-tag>
              </template>
            </van-cell>
          </van-cell-group>
        </van-list>
        <van-empty v-if="finished && !purchaseList.length" description="暂无采购订单" />
      </van-tab>
    </van-tabs>

    <!-- 新建按钮 -->
    <div class="fab-wrap">
      <van-button round type="primary" icon="plus" @click="showCreate = true">新建</van-button>
    </div>

    <!-- 订单详情弹窗 -->
    <van-popup v-model:show="showDetailPopup" position="bottom" round :style="{ height: '80%' }">
      <van-nav-bar :title="detailTitle" left-arrow @click-left="showDetailPopup = false" />
      <div v-if="currentDetail" class="detail-body">
        <van-cell-group inset title="基本信息" style="margin-top:10px">
          <van-cell title="单号" :value="currentDetail.code" />
          <van-cell title="类型" :value="orderTypeLabel" />
          <van-cell title="状态" :value="statusText(currentDetail.status)">
            <template #value><van-tag :type="tagType(currentDetail.status)">{{ statusText(currentDetail.status) }}</van-tag></template>
          </van-cell>
          <van-cell title="金额" :value="'¥ ' + (currentDetail.finalAmount || 0)" />
          <van-cell title="创建时间" :value="fmt(currentDetail.createdAt, 'createdAt')" />
        </van-cell-group>
        <van-cell-group inset title="明细" style="margin-top:10px">
          <van-cell v-for="(line, idx) in detailLines" :key="idx" :title="line.productName || ('产品' + line.productId)"
            :label="'数量：' + (line.qty || 0) + ' · 单价：¥' + (line.price || 0)"
            :value="'¥ ' + ((line.qty || 0) * (line.price || 0)).toFixed(2)" />
          <van-empty v-if="!detailLines.length" description="暂无明细" />
        </van-cell-group>
      </div>
    </van-popup>

    <!-- 新建订单弹窗 -->
    <van-popup v-model:show="showCreate" position="bottom" round style="height:70%">
      <van-nav-bar title="新建订单" left-arrow @click-left="showCreate = false" />
      <div class="create-body">
        <van-form @submit="onCreate">
          <van-cell-group inset>
            <van-field v-model="createForm.orderType" is-link readonly label="订单类型" placeholder="选择类型" @click="showTypePicker = true" />
            <van-field v-model="createForm.partnerId" type="digit" label="合作方ID" placeholder="经销商/供应商ID" :rules="[{ required: true }]" />
            <van-field v-model="createForm.warehouseId" type="digit" label="仓库ID" placeholder="仓库ID" :rules="[{ required: true }]" />
            <van-field v-model="createForm.remark" rows="2" autosize label="备注" type="textarea" placeholder="备注" />
          </van-cell-group>
          <div style="margin:16px;">
            <van-button round block type="primary" native-type="submit" :loading="creating">提交</van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <van-popup v-model:show="showTypePicker" position="bottom">
      <van-picker :columns="typeColumns" @confirm="onTypeConfirm" @cancel="showTypePicker = false" />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { showToast } from 'vant'
import { listResource, getResource, createResource } from '@/api/crud'
import { statusText, statusTagType } from '@/utils/dict'
import { fmt } from '@/utils/dict'

const activeTab = ref('sales')
const salesList = ref([])
const purchaseList = ref([])
const loading = ref(false)
const finished = ref(false)
let page = 1

const showDetailPopup = ref(false)
const currentDetail = ref(null)
const currentDetailType = ref('sales')
const detailTitle = computed(() => currentDetailType.value === 'sales' ? '销售订单详情' : '采购订单详情')
const orderTypeLabel = computed(() => currentDetailType.value === 'sales' ? '销售订单' : '采购订单')
const detailLines = computed(() => currentDetail.value?.lines || currentDetail.value?.items || [])

const showCreate = ref(false)
const showTypePicker = ref(false)
const creating = ref(false)
const createForm = ref({ orderType: '销售订单', partnerId: '', warehouseId: '', remark: '' })
const typeColumns = [
  { text: '销售订单', value: 'sales' },
  { text: '采购订单', value: 'purchase' }
]

function tagType(s) { return statusTagType(s) }

function onTabChange() {
  if (activeTab.value === 'sales' && !salesList.value.length && !finished.value) {
    page = 1; finished.value = false; onLoad()
  } else if (activeTab.value === 'purchase' && !purchaseList.value.length && !finished.value) {
    page = 1; finished.value = false; onLoad()
  }
}

async function onLoad() {
  loading.value = true
  try {
    const isSales = activeTab.value === 'sales'
    const api = isSales ? '/api/orders' : '/api/purchase-orders'
    const r = await listResource(api, { page, size: 20 })
    const d = r.data
    const rows = Array.isArray(d) ? d : (d.list || d.records || [])
    if (isSales) {
      salesList.value.push(...rows)
    } else {
      purchaseList.value.push(...rows)
    }
    page++
    if (rows.length < 20) finished.value = true
  } catch (e) { finished.value = true } finally { loading.value = false }
}

async function showDetail(o, type) {
  currentDetailType.value = type
  try {
    const api = type === 'sales' ? '/api/orders' : '/api/purchase-orders'
    const res = await getResource(api, o.id)
    currentDetail.value = res.data || o
    showDetailPopup.value = true
  } catch (e) {
    currentDetail.value = o
    showDetailPopup.value = true
  }
}

function onTypeConfirm({ selectedOptions }) {
  createForm.value.orderType = selectedOptions[0].text
  showTypePicker.value = false
}

async function onCreate() {
  const isSales = createForm.value.orderType === '销售订单'
  const api = isSales ? '/api/orders' : '/api/purchase-orders'
  const data = {
    [isSales ? 'dealerId' : 'supplierId']: Number(createForm.value.partnerId),
    warehouseId: Number(createForm.value.warehouseId),
    remark: createForm.value.remark,
    lines: []
  }
  if (!isSales) {
    data.orderType = 'NORMAL'
  }
  creating.value = true
  try {
    await createResource(api, data)
    showToast.success('创建成功')
    showCreate.value = false
    createForm.value = { orderType: '销售订单', partnerId: '', warehouseId: '', remark: '' }
    if (isSales) { salesList.value = []; page = 1; finished.value = false; onLoad() }
    else { purchaseList.value = []; page = 1; finished.value = false; onLoad() }
  } catch (e) {
    showToast.fail('创建失败')
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.amt { color: #ee0a24; font-weight: 600; }
.fab-wrap { position: fixed; right: 16px; bottom: 80px; z-index: 10; }
.detail-body { padding-bottom: 20px; }
.create-body { padding: 10px 0 20px; }
</style>
