<template>
  <div>
    <van-nav-bar title="手术植入报台" left-arrow @click-left="$router.back()" />
    <van-form @submit="onSubmit">
      <van-cell-group inset title="基础信息" style="margin-top:10px">
        <van-field v-model="form.hospitalName" label="医院名称" placeholder="请输入医院名称" :rules="[{ required: true, message: '请填写医院' }]" />
        <van-field v-model="form.patientName" label="患者姓名" placeholder="请输入患者姓名" :rules="[{ required: true, message: '请填写患者' }]" />
        <van-field v-model="form.surgeryDate" is-link readonly label="手术日期" placeholder="选择日期" @click="showDatePicker = true" />
        <van-field v-model="form.surgeon" label="主刀医生" placeholder="请输入主刀医生" />
      </van-cell-group>

      <van-cell-group inset title="产品信息" style="margin-top:10px">
        <van-field v-model="form.productName" label="产品名称" placeholder="请输入产品名称" :rules="[{ required: true, message: '请填写产品' }]" />
        <van-field v-model="form.batchNo" label="批号" placeholder="请输入批号" />
        <van-field v-model.number="form.qty" type="digit" label="数量" placeholder="请输入数量" :rules="[{ required: true, message: '请填写数量' }]" />
      </van-cell-group>

      <van-cell-group inset title="备注" style="margin-top:10px">
        <van-field v-model="form.remark" rows="3" autosize label="备注" type="textarea" placeholder="可选" />
      </van-cell-group>

      <div style="margin:16px;">
        <van-button round block type="primary" native-type="submit" :loading="submitting">提交报台</van-button>
      </div>
    </van-form>

    <van-popup v-model:show="showDatePicker" position="bottom">
      <van-date-picker @confirm="onDateConfirm" @cancel="showDatePicker = false" />
    </van-popup>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showToast } from 'vant'
import request from '@/utils/request'

const form = ref({
  hospitalName: '',
  patientName: '',
  surgeryDate: '',
  surgeon: '',
  productName: '',
  batchNo: '',
  qty: 1,
  remark: ''
})
const submitting = ref(false)
const showDatePicker = ref(false)

function onDateConfirm({ selectedValues }) {
  form.value.surgeryDate = selectedValues.join('-')
  showDatePicker.value = false
}

async function onSubmit() {
  submitting.value = true
  try {
    await request.post('/api/surgery-reports', form.value)
    showToast.success('报台成功')
    Object.assign(form.value, { hospitalName: '', patientName: '', surgeryDate: '', surgeon: '', productName: '', batchNo: '', qty: 1, remark: '' })
  } catch (e) {
    showToast.fail('报台失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.amt { color: #ee0a24; font-weight: 600; }
</style>
