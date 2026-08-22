<template>
  <div class="purchase-return-edit">
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
      <template #header><el-icon><Document /></el-icon>采退信息</template>
      <el-form label-width="110px" size="default" :disabled="readonly">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12" :md="8" :lg="8">
            <el-form-item label="供应商" required>
              <ResourcePicker resource="suppliers" v-model="form.supplierId" :display-value="form.supplierName" @pick="onPickSupplier" placeholder="选择供应商" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="8">
            <el-form-item label="出库仓库" required>
              <ResourcePicker resource="warehouses" v-model="form.warehouseId" :display-value="form.warehouseName" @pick="onPickWarehouse" placeholder="选择出库仓库" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="8">
            <el-form-item label="期望日期">
              <el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="退货原因">
              <el-input v-model="form.returnReason" type="textarea" :rows="2" placeholder="选填" />
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
      <template #header><el-icon><Goods /></el-icon>采退明细
        <div style="float:right"><el-button v-if="!readonly" type="primary" size="small" @click="addLine"><el-icon><Plus /></el-icon>添加明细</el-button></div>
      </template>
      <el-table :data="form.lines" border size="small">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="产品" min-width="260">
          <template #default="{ row }">
            <ResourcePicker v-if="!readonly" resource="products" v-model="row.productId" :display-value="row.productName" @pick="(p)=>onPickProduct(row,p)" placeholder="选择产品" />
            <span v-else>{{ row.productCode }} / {{ row.productName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="规格" min-width="120" prop="productSpec" />
        <el-table-column label="数量" width="140">
          <template #default="{ row }">
            <el-input-number v-if="!readonly" v-model="row.qty" :min="1" :precision="0" size="small" style="width:100%" controls-position="right" />
            <span v-else>{{ row.qty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="140">
          <template #default="{ row }">
            <el-input-number v-if="!readonly" v-model="row.unitPrice" :min="0" :precision="2" size="small" style="width:100%" controls-position="right" />
            <span v-else>{{ row.unitPrice }}</span>
          </template>
        </el-table-column>
        <el-table-column label="税率" width="120">
          <template #default="{ row }">
            <el-input-number v-if="!readonly" v-model="row.taxRate" :min="0" :max="1" :step="0.01" :precision="2" size="small" style="width:100%" controls-position="right" />
            <span v-else>{{ row.taxRate }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!readonly" label="操作" width="70" fixed="right">
          <template #default="{ $index }"><el-button link type="danger" size="small" @click="form.lines.splice($index,1)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <el-empty v-if="form.lines.length===0" description="请添加采退明细" />
    </el-card>

    <el-card v-if="totals" shadow="never" style="margin-top:14px">
      <template #header>退货汇总</template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="应发总数">{{ totals.expected }}</el-descriptions-item>
        <el-descriptions-item label="已发总数">{{ totals.shipped }}</el-descriptions-item>
        <el-descriptions-item label="已取消">{{ totals.cancelled }}</el-descriptions-item>
        <el-descriptions-item label="剩余">{{ totals.remaining }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="readonly && logs.length" shadow="never" style="margin-top:14px">
      <template #header><el-icon><Tickets /></el-icon>操作日志</template>
      <el-timeline>
        <el-timeline-item v-for="(log, idx) in logs" :key="idx" :timestamp="log.atTime || log.createdAt" placement="top">
          <div class="log-head">
            <el-tag size="small">{{ log.username || log.operator || log.operatorName || '系统' }}</el-tag>
            <span class="log-action">{{ log.action }}</span>
          </div>
          <div class="log-changes" v-if="log.changes || log.remark">{{ log.changes || log.remark }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, Document, Goods, Plus, Tickets } from '@element-plus/icons-vue'
import request from '@/utils/request'
import ResourcePicker from '@/components/ResourcePicker.vue'
import { getOperationLogs } from '@/api/crud'

const route = useRoute(); const router = useRouter()
const id = route.params.id
const readonly = computed(() => id && id !== 'new')

const form = reactive({ supplierId:null, supplierName:'', warehouseId:null, warehouseName:'', returnReason:'', remark:'', expectedDate:'', lines:[] })
const saving = ref(false); const acting = ref(false)
const doc = ref({})
const logs = ref([])
const canSubmit = computed(() => doc.value.status === 'DRAFT')
const canApprove = computed(() => doc.value.status === 'SUBMITTED')
const canReject = computed(() => doc.value.status === 'SUBMITTED')
const canCancel = computed(() => ['DRAFT','APPROVED'].includes(doc.value.status))
const totals = computed(() => {
  if (!doc.value.id) return null
  const exp = (doc.value.lines||[]).reduce((s,l)=>s+Number(l.qty||0),0)
  return { expected: exp, shipped: 0, cancelled: 0, remaining: exp }
})

function onPickSupplier(p){ if(!p){form.supplierId=null;form.supplierName='';return} form.supplierId=p.value; form.supplierName=p.label }
function onPickWarehouse(p){ if(!p){form.warehouseId=null;form.warehouseName='';return} form.warehouseId=p.value; form.warehouseName=p.label }
function onPickProduct(row,p){
  if(!p){ row.productId=null; return }
  row.productId=p.value
  row.productName=p.label
  row.productCode=p.row && p.row.code
  row.productSpec=p.row && p.row.spec
  if (p.row && p.row.price != null) row.unitPrice = p.row.price
  if (row.taxRate == null) row.taxRate = 0.13
}
function addLine(){ form.lines.push({ qty:1, unitPrice:0, taxRate:0.13 }) }

function validate(){
  if (!form.supplierId) return '请选择供应商'
  if (!form.warehouseId) return '请选择出库仓库'
  if (!form.lines.length) return '请添加采退明细'
  for (const l of form.lines){ if(!l.productId) return '存在未选择产品的行'; if(Number(l.qty)<=0) return '数量必须大于 0' }
  return null
}

async function save(){
  const err = validate(); if(err){ ElMessage.warning(err); return }
  saving.value = true
  try {
    const payload = { ...form, lines: form.lines.map(l=>({ productId:l.productId, qty:l.qty, unitPrice:l.unitPrice||0, taxRate:l.taxRate||0.13 })) }
    if (readonly.value){ await request({url:'/api/purchase-returns/'+id, method:'put', data:payload}); ElMessage.success('已更新'); load() }
    else { const res=await request({url:'/api/purchase-returns', method:'post', data:payload}); ElMessage.success('已创建'); router.replace('/purchase-return-edit/'+res.data.id) }
  } catch(e){ ElMessage.error(e.message||'保存失败') } finally { saving.value=false }
}

async function doAction(act){
  if(['approve','cancel','reject'].includes(act)){ try{await ElMessageBox.confirm('确认执行该操作？','提示',{type:'warning'})}catch{return} }
  acting.value=true
  try { await request({url:'/api/purchase-returns/'+id+'/'+act,method:'post'}); ElMessage.success('操作成功'); load() }
  catch(e){ ElMessage.error(e.message||'操作失败') } finally { acting.value=false }
}

async function load(){
  const res=await request({url:'/api/purchase-returns/'+id,method:'get'})
  const d=res.data||{}; doc.value=d
  Object.assign(form,{
    supplierId:d.supplierId, supplierName:d.supplierName||'', warehouseId:d.warehouseId, warehouseName:d.warehouseName||'',
    returnReason:d.returnReason||'', remark:d.remark||'', expectedDate:d.expectedDate||'',
    lines:(d.lines||[]).map(l=>({ ...l, qty:Number(l.qty), unitPrice:Number(l.unitPrice||0), taxRate:Number(l.taxRate||0.13) }))
  })
  try {
    const lr = await getOperationLogs('purchase_return', id, 'purchaseReturn')
    logs.value = Array.isArray(lr?.data) ? lr.data : []
  } catch { logs.value = [] }
}
onMounted(()=>{ if(readonly.value) load() })
</script>

<style scoped>
.log-head { display: flex; gap: 8px; align-items: center; }
.log-action { color: var(--el-text-color-regular); }
.log-changes { color: var(--el-text-color-secondary); font-size: 13px; margin-top: 4px; white-space: pre-wrap; }
</style>
