<template>
  <van-popup :show="show" position="bottom" round @update:show="$emit('update:show', $event)">
    <van-nav-bar title="选择送货地址" :left-arrow="false">
      <template #right>
        <van-button size="small" type="primary" @click="$emit('update:show', false)">关闭</van-button>
      </template>
    </van-nav-bar>
    <van-loading v-if="loading" style="display:flex;justify-content:center;padding:24px" />
    <van-empty v-else-if="!addresses.length" description="暂无收货地址，请联系销售或在资料中维护" />
    <div v-else style="max-height:60vh;overflow-y:auto">
      <van-cell
        v-for="addr in addresses"
        :key="addr.id"
        clickable
        :title="addrTitle(addr)"
        :label="addrLabel(addr)"
        @click="onPick(addr)"
      >
        <template #right-icon>
          <van-icon v-if="modelValue && modelValue.id === addr.id" name="success" color="var(--dms-color-primary,#1989fa)" />
        </template>
      </van-cell>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, watch } from 'vue'
import { showToast } from 'vant'
import { listDealerAddresses } from '@/api/mobileV43'

const props = defineProps({
  show: { type: Boolean, default: false },
  dealerId: { type: [Number, String], default: null },
  modelValue: { type: Object, default: null }
})
const emit = defineEmits(['update:show', 'update:modelValue'])

const addresses = ref([])
const loading = ref(false)

function addrTitle(a) {
  const name = a.addressName || '收货地址'
  const def = a.isDefault ? '（默认）' : ''
  return `${name}${def} · ${a.contactName || ''} ${a.phone || ''}`.trim()
}
function addrLabel(a) {
  return [a.province, a.city, a.district, a.address].filter(Boolean).join('')
}

async function load() {
  if (!props.dealerId) return
  loading.value = true
  try {
    const res = await listDealerAddresses(props.dealerId)
    const list = Array.isArray(res?.data) ? res.data : (res?.data?.list || [])
    addresses.value = list.slice().sort((x, y) => Number(y.isDefault || 0) - Number(x.isDefault || 0))
    if (!props.modelValue && addresses.value.length) {
      const def = addresses.value.find(a => a.isDefault) || addresses.value[0]
      emit('update:modelValue', def)
    }
  } catch (e) {
    showToast(e?.message || '地址加载失败')
  } finally {
    loading.value = false
  }
}

function onPick(addr) {
  emit('update:modelValue', addr)
  emit('update:show', false)
}

watch(() => props.show, (v) => { if (v) load() })
</script>
