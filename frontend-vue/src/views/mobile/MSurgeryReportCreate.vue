<template>
  <div>
    <van-nav-bar title="手术植入报台" left-arrow @click-left="$router.back()" />

    <van-cell-group inset title="基本信息" style="margin-top:10px">
      <van-field
        readonly clickable is-link required
        label="经销商"
        :model-value="form.dealerId ? dealerMap[form.dealerId] : ''"
        placeholder="选择经销商（您负责的）"
        @click="showDealerPicker = true"
      />
      <van-field
        readonly clickable is-link required
        label="医院"
        :model-value="form.terminalId ? hospitalMap[form.terminalId] : ''"
        :placeholder="form.dealerId ? '选择医院（须在授权范围内）' : '请先选择经销商'"
        :disabled="!form.dealerId"
        @click="showHospitalPicker = true"
      />
      <van-field
        readonly clickable is-link required
        label="仓库"
        :model-value="form.warehouseId ? warehouseMap[form.warehouseId] : ''"
        placeholder="选择仓库（扣减合格库存）"
        @click="showWarehousePicker = true"
      />
      <van-field
        readonly clickable is-link required
        label="手术日期"
        :model-value="form.surgeryDate"
        placeholder="选择日期"
        @click="openDatePicker = true"
      />
      <van-field
        v-model="form.patientInfo" label="患者姓名" required
        placeholder="请输入患者姓名" :rules="[{ required: true, message: '请填写患者' }]"
      />
      <van-field
        v-model="form.doctorName" label="主刀医生"
        placeholder="请输入主刀医生"
      />
      <van-field
        v-model="form.remark" label="备注" type="textarea" rows="2" autosize
        placeholder="选填"
      />
    </van-cell-group>

    <van-cell-group inset title="产品明细" style="margin-top:10px">
      <div v-for="(line, idx) in lines" :key="idx" class="line-card">
        <div class="line-head">
          <span class="line-no">#{{ idx + 1 }}</span>
          <van-button size="mini" type="danger" plain @click="removeLine(idx)" v-if="lines.length > 1">删除</van-button>
        </div>
        <van-field
          readonly clickable is-link
          label="产品"
          :model-value="line.productId ? productLabel(line) : ''"
          :placeholder="form.dealerId ? '选择产品（按授权过滤）' : '请先选择经销商'"
          :disabled="!form.dealerId"
          @click="openProductPicker(idx)"
        />
        <van-field
          v-if="line.productId"
          v-model="line.batchNo"
          :label="line.isSerialManaged ? '序列号' : '批号'"
          :placeholder="line.isSerialManaged ? '请输入序列号' : '请输入批号'"
          required
        />
        <van-field
          v-if="line.productId"
          v-model.number="line.qty" type="digit" label="数量"
          placeholder="请输入数量" required
        />
      </div>
      <div style="padding: 10px 16px;">
        <van-button block plain icon="plus" @click="addLine">添加产品</van-button>
      </div>
    </van-cell-group>

    <div class="submit-bar">
      <van-button block round type="primary" :loading="submitting" @click="submit">提交报台</van-button>
    </div>

    <!-- 选择器 -->
    <van-popup v-model:show="showDealerPicker" position="bottom" round>
      <van-picker
        :columns="dealerColumns" :model-value="[form.dealerId || '']"
        @confirm="onDealerConfirm" @cancel="showDealerPicker = false" show-toolbar
      />
    </van-popup>
    <van-popup v-model:show="showHospitalPicker" position="bottom" round>
      <van-picker
        :columns="hospitalColumns" :model-value="[form.terminalId || '']"
        @confirm="onHospitalConfirm" @cancel="showHospitalPicker = false" show-toolbar
      />
    </van-popup>
    <van-popup v-model:show="showWarehousePicker" position="bottom" round>
      <van-picker
        :columns="warehouseColumns" :model-value="[form.warehouseId || '']"
        @confirm="onWarehouseConfirm" @cancel="showWarehousePicker = false" show-toolbar
      />
    </van-popup>
    <van-popup v-model:show="showProductPicker" position="bottom" round :style="{ height: '70%' }">
      <van-nav-bar title="选择产品" :left-arrow="false">
        <template #right>
          <van-button size="small" type="primary" @click="showProductPicker = false">关闭</van-button>
        </template>
      </van-nav-bar>
      <van-search v-model="productKeyword" placeholder="搜索编码 / 名称 / 规格" />
      <van-list :loading="loadingProducts" :finished="finishedProducts" finished-text="没有更多了" @load="loadProducts">
        <van-cell
          v-for="p in productOptions" :key="p.value"
          :title="p.label" :label="(p.spec || '') + (p.isSerialManaged ? ' · 序列号管理' : '')"
          clickable @click="onProductPick(p)"
        />
      </van-list>
    </van-popup>
    <van-popup v-model:show="openDatePicker" position="bottom" round>
      <van-date-picker
        :model-value="datePickerValue"
        @confirm="onDateConfirm" @cancel="openDatePicker = false" title="选择手术日期"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { lookup } from '@/api/crud'
import request from '@/utils/request'

const router = useRouter()

const form = reactive({
  dealerId: '',
  terminalId: '',
  warehouseId: '',
  surgeryDate: new Date().toISOString().split('T')[0],
  patientInfo: '',
  doctorName: '',
  remark: ''
})
const lines = ref([{ productId: '', productName: '', spec: '', batchNo: '', qty: 1, isSerialManaged: false }])

const dealerOptions = ref([])
const dealerMap = computed(() => Object.fromEntries(dealerOptions.value.map(d => [d.value, d.label])))
const dealerColumns = computed(() => [
  { text: '请选择', value: '' },
  ...dealerOptions.value.map(d => ({ text: d.label, value: d.value }))
])

const hospitalOptions = ref([])
const hospitalMap = computed(() => Object.fromEntries(hospitalOptions.value.map(h => [h.value, h.label])))
const hospitalColumns = computed(() => [
  { text: '请选择', value: '' },
  ...hospitalOptions.value.map(h => ({ text: h.label, value: h.value }))
])

const warehouseOptions = ref([])
const warehouseMap = computed(() => Object.fromEntries(warehouseOptions.value.map(w => [w.value, w.label])))
const warehouseColumns = computed(() => [
  { text: '请选择', value: '' },
  ...warehouseOptions.value.map(w => ({ text: w.label, value: w.value }))
])

const productOptions = ref([])
const productKeyword = ref('')
const loadingProducts = ref(false)
const finishedProducts = ref(false)
let productPage = 1
let activeLineIdx = -1
const showProductPicker = ref(false)

const showDealerPicker = ref(false)
const showHospitalPicker = ref(false)
const showWarehousePicker = ref(false)
const openDatePicker = ref(false)
const datePickerValue = ref(form.surgeryDate.split('-'))

const submitting = ref(false)

function productLabel(line) {
  return line.productName + (line.spec ? ' / ' + line.spec : '')
}

async function loadDealers() {
  try {
    const r = await lookup('dealers', { limit: 100 })
    dealerOptions.value = (r.data || []).map(d => ({ value: d.id, label: (d.code ? d.code + ' · ' : '') + d.name }))
  } catch (e) { /* ignore */ }
}
async function loadHospitals() {
  try {
    const r = await lookup('hospitals', { limit: 200 })
    hospitalOptions.value = (r.data || []).map(h => ({ value: h.id, label: h.name }))
  } catch (e) { /* ignore */ }
}
async function loadWarehouses() {
  try {
    const r = await lookup('warehouses', { limit: 100 })
    warehouseOptions.value = (r.data || []).map(w => ({ value: w.id, label: w.name }))
  } catch (e) { /* ignore */ }
}

function onDealerConfirm({ selectedOptions }) {
  form.dealerId = selectedOptions[0]?.value || ''
  form.terminalId = ''
  showDealerPicker.value = false
  // 清空产品行
  lines.value = [{ productId: '', productName: '', spec: '', batchNo: '', qty: 1, isSerialManaged: false }]
}
function onHospitalConfirm({ selectedOptions }) {
  form.terminalId = selectedOptions[0]?.value || ''
  showHospitalPicker.value = false
}
function onWarehouseConfirm({ selectedOptions }) {
  form.warehouseId = selectedOptions[0]?.value || ''
  showWarehousePicker.value = false
}
function onDateConfirm({ selectedValues }) {
  form.surgeryDate = selectedValues.join('-')
  datePickerValue.value = selectedValues
  openDatePicker.value = false
}

function openProductPicker(idx) {
  if (!form.dealerId) { showToast('请先选择经销商'); return }
  activeLineIdx = idx
  productOptions.value = []
  productPage = 1
  finishedProducts.value = false
  showProductPicker.value = true
  loadProducts()
}

async function loadProducts() {
  loadingProducts.value = true
  try {
    const r = await lookup('products', {
      page: productPage, size: 30,
      keyword: productKeyword.value || undefined,
      dealerId: form.dealerId
    })
    const d = r.data
    const list = Array.isArray(d) ? d : (d.list || d.records || [])
    productOptions.value.push(...list.map(p => ({
      value: p.id,
      label: (p.code ? p.code + ' · ' : '') + (p.name || p.nameCn || ''),
      spec: p.spec || '',
      isSerialManaged: !!p.isSerialManaged
    })))
    productPage++
    if (list.length < 30) finishedProducts.value = true
  } catch (e) { finishedProducts.value = true } finally { loadingProducts.value = false }
}

function onProductPick(p) {
  if (activeLineIdx < 0) return
  const line = lines.value[activeLineIdx]
  line.productId = p.value
  line.productName = p.label
  line.spec = p.spec
  line.isSerialManaged = p.isSerialManaged
  line.batchNo = ''
  showProductPicker.value = false
  activeLineIdx = -1
}

function addLine() {
  lines.value.push({ productId: '', productName: '', spec: '', batchNo: '', qty: 1, isSerialManaged: false })
}
function removeLine(i) { lines.value.splice(i, 1) }

async function submit() {
  if (!form.dealerId) { showToast('请选择经销商'); return }
  if (!form.terminalId) { showToast('请选择医院'); return }
  if (!form.warehouseId) { showToast('请选择仓库'); return }
  if (!form.surgeryDate) { showToast('请选择手术日期'); return }
  if (!form.patientInfo) { showToast('请输入患者姓名'); return }
  const validLines = lines.value.filter(l => l.productId && l.qty > 0)
  if (!validLines.length) { showToast('请至少添加一项有效产品'); return }
  for (const l of validLines) {
    if (l.isSerialManaged && !l.batchNo) { showToast('序列号管理产品必须填写序列号'); return }
    if (!l.isSerialManaged && !l.batchNo) { showToast('批次管理产品必须填写批号'); return }
    if (!(l.qty > 0)) { showToast('数量必须大于 0'); return }
  }

  try {
    await showConfirmDialog({ title: '确认提交', message: `本次报台将扣减合格库存，确认提交？` })
  } catch (e) { return }

  submitting.value = true
  try {
    const payload = {
      dealerId: Number(form.dealerId),
      terminalId: Number(form.terminalId),
      warehouseId: Number(form.warehouseId),
      surgeryDate: form.surgeryDate,
      patientInfo: form.patientInfo,
      doctorName: form.doctorName,
      remark: form.remark,
      lines: validLines.map(l => {
        const out = { productId: l.productId, qty: l.qty }
        if (l.isSerialManaged) out.serialNo = l.batchNo
        else out.batchNo = l.batchNo
        return out
      })
    }
    const res = await request.post('/api/surgery-reports', payload)
    showToast.success('报台成功')
    const newId = res?.data?.id
    if (newId) router.replace('/mobile/surgery-reports/' + newId)
    else router.replace('/mobile/surgery-reports')
  } catch (e) {
    showToast.fail(e?.message || '报台失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadDealers()
  loadHospitals()
  loadWarehouses()
})
</script>

<style scoped>
.line-card { padding: 10px 16px; border-bottom: 1px solid #f2f3f5; }
.line-card:last-child { border-bottom: 0; }
.line-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.line-no { font-size: 13px; color: #969799; }
.submit-bar { position: fixed; bottom: 0; left: 0; right: 0; padding: 10px 16px; background: #fff; box-shadow: 0 -2px 8px rgba(0,0,0,.05); z-index: 10; }
</style>