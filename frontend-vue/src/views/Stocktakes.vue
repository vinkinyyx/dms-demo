<template>
  <div class="stocktake-page">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="title">库存盘点</span>
        <div style="flex:1" />
        <el-button type="primary" @click="openUpload">上传盘点单</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="periodYyyymm" label="盘点期间" width="120" />
        <el-table-column label="明细数" width="100">
          <template #default="{ row }">{{ row.diffSummary?.totalLines ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="差异合计(绝对值)" width="160">
          <template #default="{ row }">{{ row.diffSummary?.totalDiffAbs ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="迟交" width="90">
          <template #default="{ row }"><el-tag v-if="row.isLate" type="warning" size="small">迟交</el-tag><span v-else>-</span></template>
        </el-table-column>
        <el-table-column prop="uploadedAt" label="上传时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="viewDetail(row)">查看</el-button></template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无盘点单，点击右上角上传盘点单" />
      <el-pagination v-if="rows.length" class="pager" background layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="size" @current-change="onPage" />
    </el-card>

    <el-dialog v-model="uploadVisible" title="上传盘点单" width="720px">
      <el-form label-width="100px">
        <el-form-item label="盘点期间">
          <el-date-picker v-model="form.periodYyyymm" type="month" value-format="YYYYMM" placeholder="选择月份" style="width:200px" />
        </el-form-item>
        <el-form-item label="导入明细">
          <el-upload action="#" :auto-upload="false" :show-file-list="false" :on-change="onFileChange" accept=".xlsx,.xls">
            <el-button>选择 Excel 文件</el-button>
            <span v-if="form.lines.length" style="margin-left:12px;color:#67c23a">已加载 {{ form.lines.length }} 行</span>
          </el-upload>
          <div class="hint">Excel 列: productId(必填)、batchNo、serialNo、bookQty、actualQty</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitUpload">提交盘点单</el-button>
          <el-button @click="uploadVisible=false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="盘点单详情" size="60%">
      <el-descriptions v-if="detail" :column="3" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="ID">{{ detail.stocktake?.id }}</el-descriptions-item>
        <el-descriptions-item label="期间">{{ detail.stocktake?.periodYyyymm }}</el-descriptions-item>
        <el-descriptions-item label="明细数">{{ detail.stocktake?.diffSummary?.totalLines }}</el-descriptions-item>
        <el-descriptions-item label="差异合计">{{ detail.stocktake?.diffSummary?.totalDiffAbs }}</el-descriptions-item>
        <el-descriptions-item label="上传时间">{{ detail.stocktake?.uploadedAt }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail?.lines || []" border max-height="500">
        <el-table-column prop="productId" label="产品ID" width="100" />
        <el-table-column prop="batchNo" label="批号" width="140" />
        <el-table-column prop="serialNo" label="序列号" width="140" />
        <el-table-column prop="bookQty" label="账面数量" width="110" />
        <el-table-column prop="actualQty" label="实盘数量" width="110" />
        <el-table-column label="差异" width="100">
          <template #default="{ row }">
            <span :style="{color: Number(row.diffQty) < 0 ? '#f56c6c' : (Number(row.diffQty) > 0 ? '#67c23a' : '#909399')}">{{ row.diffQty }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import * as XLSX from 'xlsx'

const rows = ref([]), loading = ref(false), total = ref(0)
const page = ref(1), size = ref(20)

async function reload(p = page.value) {
  page.value = p; loading.value = true
  try {
    const { data } = await request({ url: '/api/stocktakes', method: 'get', params: { page: p, size: size.value } })
    rows.value = data?.list || data?.records || []
    total.value = data?.total || 0
  } finally { loading.value = false }
}
function onPage(p) { reload(p) }

const uploadVisible = ref(false)
const form = reactive({ periodYyyymm: '', stocktake: {}, lines: [] })

async function onFileChange(file) {
  const buf = await file.raw.arrayBuffer()
  const wb = XLSX.read(buf, { type: 'array' })
  const ws = wb.Sheets[wb.SheetNames[0]]
  const json = XLSX.utils.sheet_to_json(ws, { defval: '' })
  form.lines = json.map(r => ({
    productId: Number(r.productId ?? r.productID ?? r['产品ID']) || null,
    batchNo: String(r.batchNo ?? r['批号'] ?? ''),
    serialNo: String(r.serialNo ?? r['序列号'] ?? ''),
    bookQty: Number(r.bookQty ?? r['账面数量'] ?? 0),
    actualQty: Number(r.actualQty ?? r['实盘数量'] ?? 0)
  })).filter(l => l.productId)
  if (!form.lines.length) ElMessage.warning('未解析到有效明细行（需包含 productId 列）')
}
function openUpload() {
  form.periodYyyymm = ''; form.stocktake = {}; form.lines = []
  uploadVisible.value = true
}
async function submitUpload() {
  if (!form.periodYyyymm) return ElMessage.warning('请选择盘点期间')
  const { data } = await request({ url: '/api/stocktakes', method: 'post', data: {
    stocktake: { periodYyyymm: form.periodYyyymm }, lines: form.lines
  }})
  ElMessage.success('盘点单已提交: #' + data.id)
  uploadVisible.value = false
  reload(1)
}

const detailVisible = ref(false), detail = ref(null)
async function viewDetail(row) {
  const { data } = await request({ url: `/api/stocktakes/${row.id}`, method: 'get' })
  detail.value = data; detailVisible.value = true
}

onMounted(() => reload(1))
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.title { font-size: 16px; font-weight: 600; }
.pager { margin-top: 12px; justify-content: flex-end; display: flex; }
.hint { color: #909399; font-size: 12px; margin-top: 4px; }
</style>
