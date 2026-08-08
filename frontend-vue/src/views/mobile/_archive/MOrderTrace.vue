<template>
  <div>
    <van-nav-bar title="订单追溯" left-arrow @click-left="$router.back()" />
    <van-search v-model="keyword" placeholder="输入订单号/产品批号" @search="onSearch" />

    <div v-if="loading" class="loading">
      <van-loading type="spinner" />
    </div>

    <div v-else-if="result">
      <van-cell-group inset title="订单信息" style="margin-top:10px">
        <van-cell title="订单号" :value="result.code" />
        <van-cell title="状态" :value="result.status" />
        <van-cell title="金额" :value="'¥ ' + (result.finalAmount || 0)" />
        <van-cell title="创建时间" :value="result.createdAt" />
      </van-cell-group>

      <van-cell-group inset title="流转节点" style="margin-top:10px">
        <van-steps direction="vertical" :active="activeStep" active-color="#07c160">
          <van-step v-for="(n, idx) in result.nodes || []" :key="idx">
            <h4>{{ n.title }}</h4>
            <p>{{ n.time }} · {{ n.operator }}</p>
          </van-step>
        </van-steps>
        <van-empty v-if="!(result.nodes || []).length" description="暂无流转记录" />
      </van-cell-group>
    </div>

    <van-empty v-else description="输入订单号查询" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { showToast } from 'vant'
import request from '@/utils/request'

const keyword = ref('')
const loading = ref(false)
const result = ref(null)

const activeStep = computed(() => (result.value?.nodes || []).length)

async function onSearch() {
  if (!keyword.value) {
    showToast('请输入订单号或批号')
    return
  }
  loading.value = true
  try {
    const res = await request.get('/api/orders/trace', { params: { keyword: keyword.value } })
    result.value = res.data?.data || res.data || null
  } catch (e) {
    result.value = null
    showToast.fail('查询失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.loading { text-align: center; padding: 40px 0; }
</style>
