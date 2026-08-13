<template>
  <div>
    <van-nav-bar title="移动审批" />
    <van-tabs v-model:active="active" @change="reload(1)" sticky>
      <van-tab title="待我审批" name="todo" />
      <van-tab title="我已审批" name="done" />
      <van-tab title="我发起的" name="submitted" />
      <van-tab title="抄送我的" name="cc" />
    </van-tabs>
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <van-cell v-for="r in rows" :key="r.id" :title="(r.businessCode || r.title || ('审批#'+r.id))" :label="label(r)" is-link @click="open(r)" >
          <template #value><van-tag type="warning" v-if="active==='todo'">待处理</van-tag><van-tag plain type="primary" class="amt">{{ bizLabel(r.businessType) }}</van-tag></template>
        </van-cell>
        <van-empty v-if="!loading && !rows.length" description="暂无审批" />
      </van-list>
    </van-pull-refresh>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { myTodoTasks, myDoneTasks, mySubmitted, myCc } from '@/api/approval'
import { BUSINESS_LABELS } from '@/views/approval/dict'
const router=useRouter(); const active=ref('todo'); const rows=ref([]); const page=ref(1); const size=ref(20); const total=ref(0); const loading=ref(false); const finished=ref(false); const refreshing=ref(false)
function bizLabel(t){return BUSINESS_LABELS[t]||t||'审批'}
function label(r){ return (r.submitterName? '发起人：'+r.submitterName+'  ':'') + (r.nodeName||'') + '  ' + fmt(r.createdAt||r.startedAt) }
function fmt(v){ return v ? String(v).replace('T',' ').slice(0,16) : '' }
async function loadMore(){ if(refreshing.value)return; loading.value=true; try{ const params={page:page.value,size:size.value}; const res=active.value==='todo'?await myTodoTasks(params):active.value==='done'?await myDoneTasks(params):active.value==='submitted'?await mySubmitted(params):await myCc(params); const d=res.data||{}; const list=d.list||[]; rows.value.push(...list); total.value=d.total||0; if(rows.value.length>=total.value||!list.length)finished.value=true; page.value++ }finally{ loading.value=false; refreshing.value=false } }
function reload(p=1){ page.value=p; rows.value=[]; finished.value=false; if(p>1)loadMore(); }
function onRefresh(){ reload(1) }
function open(r){ const id=r.instanceId||r.id; if(id) router.push('/mobile/approvals/'+id) }
</script>
<style scoped>.amt{font-size:12px;color:#969799;margin-left:8px}</style>
