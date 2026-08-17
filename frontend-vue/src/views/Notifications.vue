<template>
  <div class="notifications-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="query.isRead" @change="reload(1)">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="false">未读</el-radio-button>
          <el-radio-button :value="true">已读</el-radio-button>
        </el-radio-group>
        <el-select v-model="query.refType" placeholder="分类" clearable style="width:160px" @change="reload(1)">
          <el-option label="审批" value="APPROVAL" />
          <el-option label="合同" value="CONTRACT" />
          <el-option label="订单" value="ORDER" />
          <el-option label="系统" value="SYSTEM" />
        </el-select>
        <el-button @click="readAll">全部已读</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe @row-click="openRow">
        <el-table-column width="70" label="状态">
          <template #default="{row}"><el-tag v-if="!row.isRead" type="danger" size="small">未读</el-tag><el-tag v-else type="info" size="small">已读</el-tag></template>
        </el-table-column>
        <el-table-column prop="title" label="标题" width="220" />
        <el-table-column prop="body" label="内容" min-width="320" show-overflow-tooltip />
        <el-table-column prop="refType" label="分类" width="110" />
        <el-table-column label="时间" width="180"><template #default="{row}">{{ fmt(row.createdAt) }}</template></el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" :current-page="query.page" :page-size="query.size" @current-change="onPage" />
    </el-card>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listNotifications, markNotificationRead, markAllNotificationsRead } from '@/api/notification'
import { ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/format'
function fmt(v){ return formatDateTime(v) }
const router=useRouter(); const rows=ref([]),loading=ref(false),total=ref(0)
const query=reactive({page:1,size:20,isRead:undefined,refType:''})
async function reload(p=query.page){query.page=p;loading.value=true;try{const r=await listNotifications(query);rows.value=r.data?.list||[];total.value=r.data?.total||0}finally{loading.value=false}}
function onPage(p){reload(p)}
async function openRow(row){ if(row.refType==='APPROVAL'&&row.refId){ await markNotificationRead(row.id); router.push('/approval/todo') } else if(!row.isRead){ await markNotificationRead(row.id); reload() } }
async function readAll(){ await markAllNotificationsRead(query.refType||undefined); ElMessage.success('已标记为已读'); reload() }
onMounted(()=>reload(1))
</script>
<style scoped>.toolbar{display:flex;gap:8px;align-items:center;margin-bottom:12px}.pager{margin-top:12px;justify-content:flex-end;display:flex}</style>
