<template>
  <div>
    <CrudView :config="config">
      <template #extra-actions>
        <el-button type="primary" v-has="'customer_voucher:manage'" @click="openIssue">
          <el-icon><Promotion /></el-icon>批量发放
        </el-button>
      </template>
    </CrudView>

    <el-dialog v-model="issueVisible" title="批量发放代金券" width="640px" :close-on-click-modal="false">
      <el-form :model="form" label-width="110px">
        <el-form-item label="发放方式" required>
          <el-radio-group v-model="form.targetType">
            <el-radio value="dealerIds">指定客户</el-radio>
            <el-radio value="dealerLevel">按客户等级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.targetType === 'dealerIds'" label="选择客户" required>
          <MultiSelectPicker v-model="form.dealerIds" resource="dealers" />
        </el-form-item>
        <el-form-item v-else label="客户等级" required>
          <el-select v-model="form.dealerLevel" placeholder="选择等级" style="width:100%">
            <el-option v-for="o in DEALER_LEVELS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="券名称" required>
          <el-input v-model="form.name" placeholder="如：金秋促销满减券" maxlength="50" />
        </el-form-item>
        <el-form-item label="面值(元)" required>
          <el-input-number v-model="form.faceValue" :min="0.01" :precision="2" controls-position="right" style="width:200px" />
          <span class="hint">下单直接抵扣，不摊入单价</span>
        </el-form-item>
        <el-form-item label="最低消费(元)">
          <el-input-number v-model="form.minSpend" :min="0" :precision="2" controls-position="right" style="width:200px" />
          <span class="hint">订单原价合计需达到该金额，0 表示无门槛</span>
        </el-form-item>
        <el-form-item label="适用范围">
          <el-select v-model="form.scopeType" style="width:200px">
            <el-option v-for="o in VOUCHER_SCOPE" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 'PRODUCT'" label="指定产品" required>
          <MultiSelectPicker v-model="form.productIds" resource="products" />
        </el-form-item>
        <el-form-item v-if="form.scopeType === 'CATEGORY'" label="指定品类" required>
          <MultiSelectPicker v-model="form.categoryIds" resource="categories" />
        </el-form-item>
        <el-form-item label="有效期" required>
          <el-date-picker v-model="validRange" type="datetimerange" range-separator="至" start-placeholder="开始时间"
            end-placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueVisible = false">取消</el-button>
        <el-button type="primary" :loading="issuing" @click="submitIssue">确认发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import CrudView from '@/components/CrudView.vue'
import MultiSelectPicker from '@/components/MultiSelectPicker.vue'
import { MODULE_CONFIGS } from '@/config/modules'
import { V430_CONSTANTS } from '@/config/v430-modules.js'
import { batchIssueVouchers } from '@/api/voucher'
import { lookup } from '@/api/crud'

const { VOUCHER_SCOPE } = V430_CONSTANTS
const DEALER_LEVELS = [
  { value: 'VIP', label: 'VIP' },
  { value: 'T1', label: '一级(T1)' },
  { value: 'T2', label: '二级(T2)' },
  { value: 'T3', label: '三级(T3)' },
  { value: 'NORMAL', label: '普通' }
]
const config = MODULE_CONFIGS['customer-vouchers']

const issueVisible = ref(false)
const issuing = ref(false)
const validRange = ref([])
const form = reactive({
  targetType: 'dealerIds',
  dealerIds: [],
  dealerLevel: '',
  name: '',
  faceValue: 100,
  minSpend: 0,
  scopeType: 'ALL',
  productIds: [],
  categoryIds: [],
  remark: ''
})

function openIssue() {
  form.targetType = 'dealerIds'
  form.dealerIds = []
  form.dealerLevel = ''
  form.name = ''
  form.faceValue = 100
  form.minSpend = 0
  form.scopeType = 'ALL'
  form.productIds = []
  form.categoryIds = []
  form.remark = ''
  validRange.value = []
  issueVisible.value = true
}

async function buildScopeRefs() {
  if (form.scopeType === 'PRODUCT') {
    if (!form.productIds.length) return null
    const res = await lookup('products', { limit: 500 })
    const all = res.data?.list || res.data || []
    return all.filter((r) => form.productIds.includes(r.id)).map((r) => ({ id: r.id, code: r.code, name: r.nameCn || r.name }))
  }
  if (form.scopeType === 'CATEGORY') {
    if (!form.categoryIds.length) return null
    const res = await lookup('categories', { limit: 500 })
    const all = res.data?.list || res.data || []
    return all.filter((r) => form.categoryIds.includes(r.id)).map((r) => ({ id: r.id, code: r.code, name: r.name }))
  }
  return []
}

async function submitIssue() {
  if (form.targetType === 'dealerIds' && (!form.dealerIds || !form.dealerIds.length)) { ElMessage.warning('请选择发放客户'); return }
  if (form.targetType === 'dealerLevel' && !form.dealerLevel) { ElMessage.warning('请选择客户等级'); return }
  if (!form.name.trim()) { ElMessage.warning('请填写券名称'); return }
  if (!form.faceValue || form.faceValue <= 0) { ElMessage.warning('面值必须大于 0'); return }
  if (form.scopeType === 'PRODUCT' && !form.productIds.length) { ElMessage.warning('请选择指定产品'); return }
  if (form.scopeType === 'CATEGORY' && !form.categoryIds.length) { ElMessage.warning('请选择指定品类'); return }
  if (!validRange.value || validRange.value.length !== 2) { ElMessage.warning('请选择有效期'); return }

  const scopeRefs = await buildScopeRefs()
  if (scopeRefs === null) { ElMessage.warning('适用范围数据加载失败，请重试'); return }

  const payload = {
    name: form.name.trim(),
    faceValue: form.faceValue,
    minSpend: form.minSpend || 0,
    scopeType: form.scopeType,
    validFrom: validRange.value[0],
    validTo: validRange.value[1],
    remark: form.remark || null
  }
  if (form.scopeType !== 'ALL') payload.scopeRefs = scopeRefs
  if (form.targetType === 'dealerIds') payload.dealerIds = form.dealerIds
  else payload.dealerLevel = form.dealerLevel

  issuing.value = true
  try {
    const res = await batchIssueVouchers(payload)
    const n = Array.isArray(res.data) ? res.data.length : 0
    ElMessage.success('发放成功，共生成 ' + n + ' 张代金券')
    issueVisible.value = false
    location.reload()
  } finally {
    issuing.value = false
  }
}
</script>

<style scoped>
.hint { margin-left: 10px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
