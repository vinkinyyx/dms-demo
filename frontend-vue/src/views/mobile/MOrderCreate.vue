<template>
  <div>
    <el-card>
      <div v-if="!dealerId" class="text-center text-gray mt-10">
        <p>请选择经销商</p>
      </div>
      <div v-else>
        <!-- 移动端订单创建 -->
        <div v-for="line in lines" :key="line.id" class="mb-4 border p-3 rounded">
          <div class="flex justify-between">
            <span>{{ line.productName }}</span>
            <el-tag size="small">{{ line.qty }} 箱</el-tag>
          </div>
        </div>
        <el-form :model="form" label-width="80px" @submit.prevent="submit">
          <el-form-item label="发货仓库" required>
            <el-select v-model="form.warehouseId" placeholder="选择仓库">
              <el-option label="请选择仓库" value="" />
              <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="要求到货日期">
            <el-date-picker v-model="form.expectedArrivalDate" type="date" placeholder="选择日期" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit">提交订单</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { createOrder } from '@/api/order'

const router = useRouter()
const dealerId = ref(null)
const lines = ref([])
const warehouses = ref([])
const form = reactive({
  dealerId: null,
  warehouseId: null,
  expectedArrivalDate: null
})

const submit = async () => {
  await createOrder(form)
  ElMessage.success('创建成功')
  router.push('/m/orders')
}
</script>

<style scoped>
.text-gray {
  color: #909393;
}
.mt-10 {
  margin-top: 10px;
}
</style>
