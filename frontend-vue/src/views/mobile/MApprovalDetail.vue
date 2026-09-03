<template>
  <div class="approval-detail">
    <van-nav-bar title="审批详情" left-arrow @click-left="$router.back()" />
    <div v-if="detail" class="body">
      <van-cell-group inset>
        <van-cell title="标题" :value="instance.title" />
        <van-cell title="类型" :value="businessLabel(instance.businessType)" />
        <van-cell title="单号" :value="instance.businessCode || instance.businessId" />
        <van-cell title="发起人" :value="instance.submitterName || instance.submitterId" />
        <van-cell title="状态"><template #value><van-tag :type="statusType(instance.status)">{{ statusLabel(instance.status) }}</van-tag></template></van-cell>
      </van-cell-group>
      <van-cell-group inset title="单据摘要">
        <van-cell v-for="(v,k) in summary.header||{}" :key="k" :title="k" :value="formatVal(v)" />
      </van-cell-group>
      <van-cell-group inset title="产品明细" v-if="(summary.items||[]).length">
        <div class="item" v-for="(it,i) in summary.items" :key="i">
          <div class="name">{{ it.productCode }} {{ it.productName }}</div>
          <div class="meta">批号:{{ it.batchNo||'-' }} 数量:{{ it.qty }} 单价:{{ it.unitPrice }} 小计:{{ it.subtotal }}</div>
        </div>
      </van-cell-group>
      <van-cell-group inset title="审批记录">
        <van-cell v-for="r in records" :key="r.id" :label="fmt(r.createdAt)">
          <template #title><b>{{ r.operatorName||r.operatorId||'系统' }}</b> <van-tag size="mini">{{ actionLabel(r.action) }}</van-tag></template>
          <template #label><span>{{ r.nodeName||'' }}</span><span v-if="r.comment"> · {{ r.comment }}</span></template>
        </van-cell>
      </van-cell-group>
      <div class="actions" v-if="myPendingTasks.length">
        <van-button block round type="primary" @click="openComment('approve')">同意</van-button>
        <van-button block round type="danger" plain @click="openComment('reject')">驳回</van-button>
      </div>
    </div>
    <van-dialog v-model:show="commentVisible" title="审批意见" show-cancel-button @confirm="submitComment">
      <van-field v-model="comment" type="textarea" placeholder="请输入审批意见" rows="3" style="margin:12px"/>
    </van-dialog>
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { useRoute } from 'vue-router'
import { showToast, showSuccessToast, showFailToast } from 'vant'
import request from '@/utils/request'
import { approveTask, rejectTask } from '@/api/approval'
const userStore = useUserStore()
import { BUSINESS_LABELS, INSTANCE_STATUS_LABELS, formatTime } from '@/views/approval/dict'
const route=useRoute(); const detail=ref(null); const summary=ref({}); const comment=ref(''); const commentVisible=ref(false); const loading=ref(false); const submitting=ref(false); let action=''
const instance=computed(()=>detail.value?.instance||{}); const tasks=computed(()=>detail.value?.tasks||[]); const records=computed(()=>detail.value?.records||[])
const currentUid=computed(()=>userStore.user&&userStore.user.id)
const myPendingTasks=computed(()=>tasks.value.filter(t=>t.status==='PENDING'&&(t.assigneeId==null||Number(t.assigneeId)===Number(currentUid.value))))
function businessLabel(t){return BUSINESS_LABELS[t]||t}
function statusLabel(s){return INSTANCE_STATUS_LABELS[s]||s}
function statusType(s){return s==='APPROVED'||s==='AUTO_APPROVED'?'success':s==='REJECTED'||s==='TERMINATED'?'danger':'warning'}
function actionLabel(a){return {APPROVE:'同意',REJECT:'驳回',TRANSFER:'转办',ADD_SIGN:'加签',WITHDRAW:'撤回',AUTO_PASS:'自动通过',CC:'抄送'}[a]||a}
function formatVal(v){return [null,undefined,''].includes(v)?'-':String(v)}
function fmt(v){return v?String(v).replace('T',' ').slice(0,16):''}
async function load(){ const id=route.params.id; loading.value=true; try{ const [d,s]=await Promise.all([request({url:'/api/approval/instances/'+id}), request({url:'/api/approval/instances/'+id+'/summary'})]); detail.value=d.data; summary.value=s.data||{} }catch(e){ showFailToast('加载审批详情失败') }finally{ loading.value=false } }
function openComment(a){ if(!myPendingTasks.value.length)return; action=a; comment.value=''; commentVisible.value=true }
async function submitComment(){ const t=myPendingTasks.value[0]; if(!t)return; submitting.value=true; try{ if(action==='approve'){await approveTask(t.id,comment.value); showSuccessToast('已同意')} else { await rejectTask(t.id,comment.value); showSuccessToast('已驳回')} commentVisible.value=false; await load() }catch(e){ showFailToast((e&&e.message)||'操作失败，请重试') }finally{ submitting.value=false } }
onMounted(load)
</script>
<style scoped>.body{padding:10px 0 80px}.item{padding:10px 16px;border-bottom:1px solid var(--dms-divider-color)}.item:last-child{border:0}.name{font-weight:600}.meta{font-size:12px;color:var(--dms-text-4);margin-top:4px}.actions{position:fixed;left:0;right:0;bottom:0;background:var(--dms-bg-container);padding:10px 16px;padding-bottom:calc(10px + env(safe-area-inset-bottom));display:grid;grid-template-columns:1fr 1fr;gap:10px;box-shadow:0 -2px 8px rgba(0,0,0,.05);z-index:10}</style>
