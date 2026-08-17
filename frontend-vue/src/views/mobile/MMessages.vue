<template>
  <div>
    <van-nav-bar title="消息中心" right-text="全部已读" @click-right="readAll" />
    <van-tabs v-model:active="active" @change="reload(1)" sticky>
      <van-tab title="全部" name="" />
      <van-tab title="未读" name="unread" />
      <van-tab title="审批" name="APPROVAL" />
    </van-tabs>
    <van-pull-refresh v-model:loading="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <van-cell v-for="n in rows" :key="n.id" :title="n.title" :label="n.body" is-link @click="open(n)">
          <template #icon><van-dot v-if="!n.isRead" style="margin-right:8px;margin-top:10px"/></template>
          <template #value><span class="time">{{ fmt(n.createdAt) }}</span></template>
        </van-cell>
        <van-empty v-if="!loading && !rows.length" description="暂无消息" />
      </van-list>
    </van-pull-refresh>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import request from '@/utils/request'
import { markNotificationRead, markAllNotificationsRead } from '@/api/notification'
const router=useRouter(); const active=ref(''); const rows=ref([]); const page=ref(1); const size=ref(20); const total=ref(0); const loading=ref(false); const finished=ref(false); const refreshing=ref(false)
function fmt(v){return v?String(v).replace('T',' ').slice(5,16):''}
async function loadMore(){loading.value=true;try{const params={page:page.value,size:size.value}; if(active.value==='unread')params.isRead=false; else if(active.value)params.refType=active.value; const r=await request({url:'/api/notifications',method:'get',params}); const d=r.data||{}; const list=d.list||[]; rows.value.push(...list); total.value=d.total||0; if(rows.value.length>=total.value||!list.length)finished.value=true; page.value++}finally{loading.value=false;refreshing.value=false}}
function reload(p=1){page.value=p;rows.value=[];finished.value=false;if(p>1)loadMore()}
function onRefresh(){reload(1)}
async function open(n){ if(!n.isRead){try{await markNotificationRead(n.id)}catch(e){}} if(n.refType==='APPROVAL'&&n.refId) router.push('/mobile/approvals/'+n.refId); else reload() }
async function readAll(){await markAllNotificationsRead(); showSuccessToast('已全部已读'); reload(1)}
</script>
<style scoped>.time{font-size:12px;color:#969799}</style>
