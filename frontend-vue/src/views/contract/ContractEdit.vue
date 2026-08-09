<template>
  <div class="contract-edit">
    <el-page-header @back="$router.back()" :content="isEdit ? '编辑合同' : '新建合同'" class="header" />
    <el-card shadow="never" v-loading="loading">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" size="default">
        <el-divider content-position="left">基础信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="合同名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入合同名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="合同分类" prop="category">
              <el-select v-model="form.category" placeholder="请选择" @change="onCategoryChange" style="width: 100%">
                <el-option v-for="o in CATEGORY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="申请类型" prop="applicationType">
              <el-select v-model="form.applicationType" style="width: 100%">
                <el-option v-for="o in APP_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="经销商" prop="dealerId">
              <ResourcePicker v-model="form.dealerId" resource="dealers" placeholder="选择经销商" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="甲方">
              <el-input v-model="form.vendorParty" placeholder="甲方/卖方" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="乙方">
              <el-input v-model="form.dealerParty" placeholder="乙方/买方" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="签订城市">
              <el-input v-model="form.signCity" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="生效日期">
              <el-date-picker v-model="form.validFrom" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="截止日期">
              <el-date-picker v-model="form.validTo" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="目标金额">
              <el-input-number v-model="form.targetAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="签约金额">
              <el-input-number v-model="form.signedAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="结算周期">
              <el-input v-model="form.settlementCycle" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="付款条款">
              <el-input v-model="form.paymentTerms" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="负责人">
              <el-input v-model="form.ownerName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系电话">
              <el-input v-model="form.ownerPhone" />
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="templateFields.length">
          <el-divider content-position="left">合同条款（模板字段）</el-divider>
          <el-alert v-if="!template" :closable="false" type="info" show-icon title="该分类暂无已发布模板，可直接保存基础信息" style="margin-bottom: 12px" />
          <el-row :gutter="16" v-else>
            <el-col v-for="f in templateFields" :key="f.key" :span="f.type === 'textarea' ? 24 : 8">
              <el-form-item :label="f.label" :prop="'formData.' + f.key" :rules="f.required ? { required: true, message: '请填写' + f.label } : null">
                <el-input v-if="f.type === 'text'" v-model="form.formData[f.key]" />
                <el-input v-else-if="f.type === 'textarea'" v-model="form.formData[f.key]" type="textarea" :rows="3" />
                <el-input-number v-else-if="f.type === 'number' || f.type === 'amount'" v-model="form.formData[f.key]" :controls="false" :precision="f.type === 'amount' ? 2 : 0" style="width:100%" />
                <el-date-picker v-else-if="f.type === 'date'" v-model="form.formData[f.key]" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                <el-select v-else-if="f.type === 'select'" v-model="form.formData[f.key]" style="width:100%">
                  <el-option v-for="opt in (f.options || [])" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <el-checkbox v-else-if="f.type === 'checkbox'" v-model="form.formData[f.key]" />
                <el-input v-else v-model="form.formData[f.key]" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <el-divider content-position="left">备注</el-divider>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <div class="footer-actions">
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="info" @click="save(false)">保存草稿</el-button>
        <el-button type="primary" @click="save(true)">保存并提交</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ResourcePicker from '@/components/ResourcePicker.vue'
import { createContract, getContract, matchTemplate, updateContract, submitContract } from './api'
import { CATEGORY_OPTIONS, APP_TYPE_OPTIONS } from './dict'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const template = ref(null)
const formRef = ref(null)
const isEdit = computed(() => !!route.params.id)

const defaultForm = () => ({
  name: '', category: '', applicationType: 'NEW', dealerId: null,
  vendorParty: '', dealerParty: '', signCity: '', validFrom: '', validTo: '',
  targetAmount: null, signedAmount: null, settlementCycle: '', paymentTerms: '',
  ownerName: '', ownerPhone: '', formData: {}, remark: ''
})
const form = reactive(defaultForm())
const rules = {
  name: [{ required: true, message: '请输入合同名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择合同分类', trigger: 'change' }]
}
const templateFields = computed(() => {
  if (!template.value || !Array.isArray(template.value.fields)) return []
  return [...template.value.fields].sort((a, b) => (a.sort || 0) - (b.sort || 0))
})

async function onCategoryChange() {
  if (!form.category) { template.value = null; form.templateId = null; return }
  try {
    const res = await matchTemplate(form.category)
    template.value = res.data || null
    if (template.value) {
      form.templateId = template.value.id
      form.templateVersion = template.value.version
    }
  } catch (e) {
    template.value = null
  }
}

async function load() {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getContract(route.params.id)
    const d = res.data || res
    Object.assign(form, {
      name: d.name, category: d.category, applicationType: d.applicationType,
      dealerId: d.dealerId, vendorParty: d.vendorParty, dealerParty: d.dealerParty,
      signCity: d.signCity, validFrom: d.validFrom, validTo: d.validTo,
      targetAmount: d.targetAmount, signedAmount: d.signedAmount,
      settlementCycle: d.settlementCycle, paymentTerms: d.paymentTerms,
      ownerName: d.ownerName, ownerPhone: d.ownerPhone,
      templateId: d.templateId, templateVersion: d.templateVersion,
      formData: d.formData || {}, remark: d.remark
    })
    if (d.template) template.value = d.template
  } finally {
    loading.value = false
  }
}

function payload() {
  return { ...form, formData: { ...form.formData } }
}

async function save(submit) {
  await formRef.value.validate()
  let id = route.params.id
  if (isEdit.value) {
    await updateContract(id, payload())
  } else {
    const res = await createContract(payload())
    id = (res.data || res).id
  }
  if (submit) {
    await submitContract(id)
    ElMessage.success('已提交审批')
  } else {
    ElMessage.success('已保存草稿')
  }
  router.push('/contracts')
}
onMounted(load)
</script>

<style scoped>
.header { margin-bottom: 12px; }
.footer-actions { text-align: right; margin-top: 8px; }
</style>