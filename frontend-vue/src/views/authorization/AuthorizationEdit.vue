<template>
  <div class="auth-edit-page" v-loading="loading">
    <el-card>
      <template #header><span>新增授权（厂家授权给经销商）</span></template>
      <el-form :model="form" label-width="110px" style="max-width:900px">
        <el-form-item label="经销商" required>
          <el-select v-model="form.dealerId" filterable remote placeholder="选择经销商"
                     :remote-method="searchDealers" :loading="dealerLoading" style="width:360px">
            <el-option v-for="d in dealers" :key="d.id" :label="d.name + (d.code ? ' (' + d.code + ')' : '')" :value="d.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="产品线" required>
          <el-select v-model="productLineIds" multiple filterable placeholder="选择授权产品线（可多选）" style="width:100%">
            <el-option v-for="p in productLines" :key="p.id" :label="p.name + (p.code ? ' (' + p.code + ')' : '')" :value="p.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="有效期" required>
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
            range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width:360px" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" style="width:480px" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top:12px">
      <template #header>
        <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
          <span>授权终端医院</span>
          <el-select v-model="regionId" clearable placeholder="按省份/区域筛选" style="width:200px" @change="loadHospitals">
            <el-option v-for="r in regionOptions" :key="r.id" :label="r.label" :value="r.id" />
          </el-select>
          <el-input v-model="hospitalKeyword" placeholder="医院名称搜索" clearable style="width:200px" @keyup.enter="loadHospitals" @clear="loadHospitals" />
          <el-button size="small" @click="loadHospitals">查询</el-button>
          <el-button size="small" type="primary" plain @click="selectAllFiltered">全选当前结果</el-button>
          <el-button size="small" @click="clearHospitals">清空选择</el-button>
          <el-tag type="success">已选 {{ terminalIds.length }} 家</el-tag>
        </div>
      </template>
      <el-table :data="hospitals" border stripe size="small" height="380" row-key="id"
                @selection-change="onSelectionChange" ref="hospitalTable">
        <el-table-column type="selection" width="48" reserve-selection />
        <el-table-column label="医院名称" prop="name" min-width="220" show-overflow-tooltip />
        <el-table-column label="编码" prop="code" width="140" />
        <el-table-column label="所属区域" prop="regionName" width="160" show-overflow-tooltip />
      </el-table>
    </el-card>

    <div style="margin-top:16px;text-align:center">
      <el-button @click="$router.back()">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">提交审批</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createAuthorization, listAuthProductLines, listAuthTerminals,
  listDealers, listRegionsTree
} from './api'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const dealers = ref([])
const dealerLoading = ref(false)
const productLines = ref([])
const productLineIds = ref([])
const hospitals = ref([])
const terminalIds = ref([])
const regionId = ref(null)
const regionOptions = ref([])
const hospitalKeyword = ref('')
const hospitalTable = ref(null)
const dateRange = ref(null)

const form = reactive({ dealerId: null, remark: '' })

async function searchDealers(keyword) {
  dealerLoading.value = true
  try {
    const res = await listDealers({ page: 1, size: 30, keyword: keyword || '' })
    dealers.value = res?.data?.list || res?.data?.records || []
  } finally { dealerLoading.value = false }
}
async function loadProductLines() {
  const res = await listAuthProductLines()
  productLines.value = res?.data || []
}
function flattenTree(nodes, depth, out) {
  for (const n of nodes || []) {
    out.push({ id: n.id, label: '　'.repeat(depth) + (n.label || n.name) })
    if (n.children && n.children.length) flattenTree(n.children, depth + 1, out)
  }
}
async function loadRegions() {
  try {
    const res = await listRegionsTree()
    const out = []
    flattenTree(res?.data || [], 0, out)
    regionOptions.value = out
  } catch (e) { /* ignore */ }
}
async function loadHospitals() {
  loading.value = true
  try {
    const params = { keyword: hospitalKeyword.value || undefined, regionId: regionId.value || undefined }
    const res = await listAuthTerminals(params)
    hospitals.value = res?.data || []
  } catch (e) {
    ElMessage.error('医院加载失败: ' + (e?.message || e))
  } finally { loading.value = false }
}
function onSelectionChange(sel) {
  terminalIds.value = sel.map(h => h.id)
}
function selectAllFiltered() {
  if (!hospitals.value.length) { ElMessage.warning('当前无结果'); return }
  hospitals.value.forEach(h => hospitalTable.value.toggleRowSelection(h, true))
  ElMessage.success('已全选当前 ' + hospitals.value.length + ' 家医院')
}
function clearHospitals() {
  hospitalTable.value?.clearSelection()
  terminalIds.value = []
}

async function submit() {
  if (!form.dealerId) return ElMessage.warning('请选择经销商')
  if (!productLineIds.value.length) return ElMessage.warning('请选择至少一个产品线')
  if (!terminalIds.value.length) return ElMessage.warning('请选择至少一家终端医院')
  if (!dateRange.value || !dateRange.value[0] || !dateRange.value[1]) return ElMessage.warning('请选择有效期')
  saving.value = true
  try {
    await createAuthorization({
      dealerId: form.dealerId,
      productLines: productLineIds.value,
      terminalIds: terminalIds.value,
      validFrom: dateRange.value[0],
      validTo: dateRange.value[1],
      remark: form.remark,
      authType: 'ORDER'
    })
    ElMessage.success('授权已提交审批')
    router.push('/authorizations')
  } catch (e) {
    ElMessage.error('提交失败: ' + (e?.response?.data?.message || e?.message || e))
  } finally { saving.value = false }
}

onMounted(() => {
  searchDealers('')
  loadProductLines()
  loadRegions()
  loadHospitals()
})
</script>
