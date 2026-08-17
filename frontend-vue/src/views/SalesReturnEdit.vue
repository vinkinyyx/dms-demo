<template>
  <div class="sales-return-edit">
    <div class="page-toolbar">
      <el-button @click="$router.back()"><el-icon><ArrowLeft /></el-icon>返回</el-button>
      <div class="spacer" />
      <template v-if="readonly">
        <el-button v-if="canSubmit" type="warning" :loading="acting" @click="doAction('submit')">提交审批</el-button>
        <el-button v-if="canApprove" type="success" :loading="acting" @click="doAction('approve')">审批通过</el-button>
        <el-button v-if="canReject" type="danger" :loading="acting" @click="doAction('reject')">驳回</el-button>
        <el-button v-if="canCancel" type="warning" :loading="acting" @click="doAction('cancel')">取消</el-button>
      </template>
      <el-button v-else type="primary" :loading="saving" @click="save"><el-icon><Check /></el-icon>保存</el-button>
    </div>

    <el-card shadow="never">
      <template #header><el-icon><Document /></el-icon>销退信息</template>
      <el-form label-width="110px" size="default" :disabled="readonly">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="经销商" required>
              <ResourcePicker resource="dealers" v-model="form.dealerId" :display-value="form.dealerName" @pick="onPick('dealer', $event)" placeholder="选择经销商" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收货仓库" required>
              <ResourcePicker resource="warehouses" v-model="form.warehouseId" :display-value="form.warehouseName" @pick="onPick('warehouse', $event)" placeholder="选择收货仓库" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="期望日期">
              <el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="原发货单" required>
              <el-select v-model="form.refSalesOutId" filterable remote :remote-method="searchShippedOuts" :loading="outLoading"
                placeholder="选择已发货的发货单" style="width:100%" @change="onShippedOutChange" :disabled="readonly">
                <el-option v-for="o in shippedOuts" :key="o.id" :value="o.id" :label="o.code + ' / ' + (o.dealerName||'') + ' / ' + (o.warehouseName||'')" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="退货原因" required>
              <el-input v-model="form.returnReason" type="textarea" :rows="2" placeholder="请填写退货原因" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top:14px">
      <template #header><el-icon><Goods /></el-icon>销退明细
        <span style="margin-left:12px;color:var(--dms-text-4);font-weight:normal">数量只能小于等于可退数量，行可删除</span>
      </template>
      <el-table :data="form.lines" border size="small">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="产品编码" min-width="120" prop="productCode" />
        <el-table-column label="产品名称" min-width="160" prop="productName" />
        <el-table-column label="规格" min-width="120" prop="productSpec" />
        <el-table-column label="批次号" width="120">
          <template #default="{ row }">{{ row.batchNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="序列号" width="150">
          <template #default="{ row }">{{ row.serialNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="已发货" width="90" prop="shippedQty" />
        <el-table-column label="可退数量" width="100" prop="returnableQty" />
        <el-table-column label="退货数量" width="140">
          <template #default="{ row }">
            <el-input-number v-if="!readonly" v-model="row.qty" :min="0" :max="Number(row.returnableQty)" :precision="0" size="small" style="width:100%" controls-position="right" />
            <span v-else>{{ row.qty }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!readonly" label="操作" width="70" fixed="right">
          <template #default="{ $index }"><el-button link type="danger" size="small" @click="form.lines.splice($index,1)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <el-empty v-if="form.lines.length===0" description="请先选择原发货单" />
    </el-card>

    <el-card v-if="totals" shadow="never" style="margin-top:14px">
      <template #header>退货汇总</template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="应退总数">{{ totals.expected }}</el-descriptions-item>
        <el-descriptions-item label="已退总数">{{ totals.received }}</el-descriptions-item>
        <el-descriptions-item label="已取消">{{ totals.cancelled }}</el-descriptions-item>
        <el-descriptions-item label="剩余">{{ totals.remaining }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, Document, Goods } from '@element-plus/icons-vue'
import request from '@/utils/request'
import ResourcePicker from '@/components/ResourcePicker.vue'

const route = useRoute()
const router = useRouter()
const id = route.params.id
const readonly = computed(() => id && id !== 'new')

const form = reactive({
  dealerId: null, dealerName: '', warehouseId: null, warehouseName: '',
  refSalesOutId: null, refSalesOutCode: '', refOrderId: null,
  returnReason: '', remark: '', expectedDate: '', lines: []
})
const saving = ref(false); const acting = ref(false); const outLoading = ref(false)
const shippedOuts = ref([])
const doc = ref({})

const canSubmit = computed(() => doc.value.status === 'DRAFT')
const canApprove = computed(() => doc.value.status === 'SUBMITTED')
const canReject = computed(() => doc.value.status === 'SUBMITTED')
const canCancel = computed(() => ['DRAFT','APPROVED'].includes(doc.value.status))
const totals = computed(() => {
  if (!doc.value.id) return null
  const exp = (doc.value.lines||[]).reduce((s,l)=>s+Number(l.qty||0),0)
  return { expected: exp, received: 0, cancelled: 0, remaining: exp }
})

function onPick(type, p) {
  if (!p) { if(type==='dealer'){form.dealerId=null;form.dealerName=''} else {form.warehouseId=null;form.warehouseName=''}; return }
  if (type==='dealer') { form.dealerId=p.value; form.dealerName=p.label }
  else { form.warehouseId=p.value; form.warehouseName=p.label }
}

async function searchShippedOuts(q) {
  outLoading.value = true
  try {
    const params = { page:1, size:50 }
    if (form.dealerId) params.dealerId = form.dealerId
    const res = await request({ url:'/api/sales-returns/shipped-outs', method:'get', params })
    shippedOuts.value = (res.data||[]).filter(o => !q || (o.code||'').includes(q) || (o.dealerName||'').includes(q))
  } finally { outLoading.value = false }
}
async function onShippedOutChange(v) {
  if (!v) { form.lines = []; return }
  const res = await request({ url:'/api/sales-returns/shipped-outs/'+v+'/lines', method:'get' })
  const d = res.data || {}
  form.lines = (d.lines||[]).map(l => ({ ...l, qty: Number(l.returnableQty) }))
  if (d.dealerId && !form.dealerId) { form.dealerId = d.dealerId }
  if (d.warehouseId && !form.warehouseId) { form.warehouseId = d.warehouseId }
  form.refOrderId = d.orderId
}

function validate() {
  if (!form.dealerId) return '请选择经销商'
  if (!form.warehouseId) return '请选择收货仓库'
  if (!form.refSalesOutId) return '请选择原发货单'
  if (!form.returnReason || !form.returnReason.trim()) return '请填写退货原因'
  if (!form.lines.length) return '请添加销退明细'
  for (const l of form.lines) {
    if (Number(l.qty)<=0) return '产品 '+(l.productName||'')+' 退货数量必须大于 0'
    if (Number(l.qty) > Number(l.returnableQty)) return '产品 '+(l.productName||'')+' 退货数量超过可退数量'
  }
  return null
}

async function save() {
  const err = validate(); if (err) { ElMessage.warning(err); return }
  saving.value = true
  try {
    const payload = { ...form, lines: form.lines.map(l=>({ productId:l.productId, batchNo:l.batchNo, serialNo:l.serialNo, qty:l.qty, unitPrice:l.unitPrice||0, taxRate:l.taxRate||0.13 })) }
    if (readonly.value) {
      await request({ url:'/api/sales-returns/'+id, method:'put', data:payload })
      ElMessage.success('已更新'); load()
    } else {
      const res = await request({ url:'/api/sales-returns', method:'post', data:payload })
      ElMessage.success('已创建')
      router.replace('/sales-return-edit/'+res.data.id)
    }
  } catch(e) { ElMessage.error(e.message||'保存失败') }
  finally { saving.value = false }
}

async function doAction(act) {
  if ((act==='approve'||act==='cancel'||act==='reject')) {
    try { await ElMessageBox.confirm('确认执行该操作？','提示',{type:'warning'}) } catch { return }
  }
  acting.value = true
  try {
    await request({ url:'/api/sales-returns/'+id+'/'+act, method:'post' })
    ElMessage.success('操作成功'); load()
  } catch(e){ ElMessage.error(e.message||'操作失败') }
  finally { acting.value = false }
}

async function load() {
  const res = await request({ url:'/api/sales-returns/'+id, method:'get' })
  const d = res.data || {}
  doc.value = d
  Object.assign(form, {
    dealerId:d.dealerId, dealerName:d.dealerName||'', warehouseId:d.warehouseId, warehouseName:d.warehouseName||'',
    refSalesOutId:d.refSalesOutId, refSalesOutCode:d.refSalesOutCode||'', refOrderId:d.refOrderId,
    returnReason:d.returnReason||'', remark:d.remark||'', expectedDate:d.expectedDate||'',
    lines:(d.lines||[]).map(l=>({ ...l, qty:Number(l.qty), returnableQty:Number(l.qty), shippedQty:Number(l.qty) }))
  })
}

onMounted(() => { if (readonly.value) load(); else searchShippedOuts() })
</script>
