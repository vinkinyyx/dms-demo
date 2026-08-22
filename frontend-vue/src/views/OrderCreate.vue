<template>
  <div class="area-page order-create-page">
    <div class="page-header"><div class="page-title"><el-button text @click="goBack"><el-icon><ArrowLeft/></el-icon></el-button><h3>{{ isEdit ? '编辑销售订单' : '新增销售订单' }}</h3></div><div class="page-actions"><el-button @click="goBack">取消</el-button><el-button type="primary" :loading="saving" @click="onSave(false)">保存草稿</el-button><el-button v-if="canEdit" type="success" :loading="submitting" @click="onSave(true)">提交</el-button></div></div><div class="area-scroll"><el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="page-alert"/><el-alert v-for="(m,i) in promoMessages" :key="i" :title="m" type="success" show-icon :closable class="page-alert"/>
    <div class="form-container">
      <el-card shadow="never"><template #header>基本信息</template>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px"><el-row :gutter="20">
          <el-col :xs="24" :sm="24" :md="16" :lg="16"><el-form-item label="经销商" prop="dealerId"><ResourcePicker resource="dealers" v-model="form.dealerId" placeholder="选择经销商" @change="onDealerChange"/></el-form-item></el-col>
          <el-col :xs="12" :sm="12" :md="4" :lg="4"><el-form-item label="订单类型"><el-select v-model="form.orderType" style="width:100%"><el-option label="销售订单" value="SALES"/><el-option label="补货订单" value="REPLENISHMENT"/></el-select></el-form-item></el-col>
          <el-col :xs="12" :sm="12" :md="4" :lg="4"><el-form-item label="期望日期"><el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="整单折扣"><div class="discount-input"><el-select v-model="form.headerDiscountType" clearable class="discount-type" placeholder="折扣类型"><el-option label="百分比" value="PERCENT"/><el-option label="固定金额" value="AMOUNT"/></el-select><el-input-number v-if="form.headerDiscountType" v-model="form.headerDiscountValue" :min="0" :precision="2" :max="form.headerDiscountType==='PERCENT'?100:undefined" controls-position="right" class="discount-value" @change="schedulePreview"/></div></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit/></el-form-item></el-col>
        </el-row></el-form>
      </el-card>
      <el-card shadow="never"><template #header><div class="lines-header"><span>订单明细</span><div><el-tag size="small" type="info">共 {{ editableRoots.length }} 个录入行</el-tag><el-tag v-if="giftLines.length" size="small" type="warning">赠品 {{ giftLines.length }} 行</el-tag><el-tag size="small" type="success">订单总价 ¥{{ finalTotal.toFixed(2) }}</el-tag><el-button size="small" :icon="Refresh" :loading="refreshing" @click="refreshPromotions">刷新赠品及价格</el-button><el-button type="primary" size="small" :icon="Plus" @click="addLine">添加行</el-button></div></div></template>
        <el-table :data="form.lines" border stripe size="small" row-key="tempId" :tree-props="{children:'children'}" :default-expand-all="true">
          <el-table-column label="产品" min-width="300"><template #default="{row}"><div class="product-cell"><ResourcePicker v-if="canPickProduct(row)" resource="products" v-model="row.productId" :display-value="row.productLabel" @pick="p=>onProductPicked(row,p)" style="flex:1"/><span v-else class="child-product-text"><span class="child-product-code">{{row.productCode}}</span><span class="child-product-name">{{row.productName}}</span></span><el-tag v-if="row.lineLevel==='PARENT'||row.isBom" size="small" type="warning">BOM母件</el-tag><el-tag v-if="row.isGift" size="small" type="danger">赠品</el-tag><el-tag v-for="t in promoTags(row)" :key="t" size="small" type="success">{{t}}</el-tag></div></template></el-table-column>
          <el-table-column prop="productSpec" label="规格" width="140" show-overflow-tooltip/><el-table-column prop="unit" label="单位" width="70" align="center"/>
          <el-table-column label="数量" width="120"><template #default="{row}"><el-input-number v-model="row.qty" :min="1" :precision="0" controls-position="right" size="small" style="width:100%" :disabled="!canEditQty(row)" @change="()=>onLineQtyChange(row)"/></template></el-table-column>
          <el-table-column label="含税单价" width="110" align="right"><template #default="{row}">{{unitPrice(row).toFixed(2)}}</template></el-table-column>
          <el-table-column label="标准金额" width="110" align="right"><template #default="{row}">{{standardAmount(row).toFixed(2)}}</template></el-table-column>
          <el-table-column label="行折扣" width="190"><template #default="{row}"><div v-if="canEditDiscount(row)" class="line-discount"><el-select v-model="row.lineDiscountType" size="small" clearable style="width:88px"><el-option label="比例" value="PERCENT"/><el-option label="金额" value="AMOUNT"/></el-select><el-input-number v-if="row.lineDiscountType" v-model="row.lineDiscountValue" :min="0" :precision="2" :max="row.lineDiscountType==='PERCENT'?100:undefined" controls-position="right" size="small" style="width:90px" @change="schedulePreview"/></div><span v-else class="muted">{{ row.isGift ? '赠品不收费' : (row.lineLevel==='PARENT' ? '母件不打折' : '-') }}</span></template></el-table-column>
          <el-table-column label="促销折扣" width="105" align="right"><template #default="{row}">{{num(row.promoDiscountAmount).toFixed(2)}}</template></el-table-column>
          <el-table-column label="出库单价" width="110" align="right"><template #default="{row}">{{(row.isGift||row.lineLevel==='PARENT'?0:num(row.finalAmount)/Math.max(num(row.qty),1)).toFixed(2)}}</template></el-table-column><el-table-column label="最终金额" width="110" align="right"><template #default="{row}"><b>{{num(row.finalAmount).toFixed(2)}}</b></template></el-table-column>
          <el-table-column label="操作" width="70" fixed="right"><template #default="{ $index, row }"><el-button v-if="canDeleteRow(row)" type="danger" link size="small" @click="removeLine($index)">删除</el-button></template></el-table-column>
        </el-table>
      </el-card>
    </div>
    </div>
  </div>
</template>
<script setup>
defineOptions({ name: 'OrderCreate' })
import {computed,nextTick,onMounted,onActivated,reactive,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {ArrowLeft,Plus,Refresh} from '@element-plus/icons-vue'
import ResourcePicker from '@/components/ResourcePicker.vue'
import request from '@/utils/request'
const route=useRoute(),router=useRouter(),isEdit=computed(()=>!!route.params.id),formRef=ref(null),saving=ref(false),submitting=ref(false),previewLoading=ref(false),refreshing=ref(false),error=ref(''),promoMessages=ref([])
let previewTimer=null
let previewToken=0
let seq=1
const form=reactive({id:null,status:'DRAFT',dealerId:null,orderType:'SALES',expectedDate:'',headerDiscountType:'',headerDiscountValue:0,remark:'',lines:[]})
const rules={dealerId:[{required:true,message:'请选择经销商',trigger:'change'}]}
const canEdit=computed(()=>!isEdit.value||['DRAFT','REJECTED'].includes(form.status))
const canDelete=computed(()=>['DRAFT','REJECTED'].includes(form.status))
const flatLines=computed(()=>flatten(form.lines))
const editableRoots=computed(()=>form.lines.filter(l=>!l.isGift&&l.lineLevel!=='CHILD'))
const giftLines=computed(()=>flatLines.value.filter(l=>l.isGift))
const finalTotal=computed(()=>Number(form.finalAmount||flatLines.value.reduce((s,l)=>s+num(l.finalAmount),0)))
const num=v=>Number(v||0)
function flatten(a){return a.flatMap(x=>[x,...(Array.isArray(x.children)?flatten(x.children):[])])}
function goBack(){router.push('/m/orders')}
function todayStr(){const d=new Date();const mm=String(d.getMonth()+1).padStart(2,'0');const dd=String(d.getDate()).padStart(2,'0');return `${d.getFullYear()}-${mm}-${dd}`}
function onDealerChange(){flatLines.value.forEach(loadPrice);schedulePreview()}
function promoTags(r){if(r.isGift)return ['促销赠品'];return r.promoNames?String(r.promoNames).split(',').filter(Boolean).map(x=>'命中:'+x):[]}
function makeLine(p={}){return {tempId:'t'+(seq++),productId:p.id||p.productId||null,productCode:p.code||p.productCode||'',productName:p.nameCn||p.name||p.productName||'',productLabel:(p.id||p.productId)?`${p.code||p.productCode||''} ${p.nameCn||p.name||p.productName||''}`.trim():'',productSpec:p.spec||p.productSpec||'',unit:p.unit||p.unitType||'EA',qty:Number(p.qty||1),standardPriceInclTax:num(p.currentPrice??p.price??p.standardPriceInclTax),taxRate:num(p.taxRate),standardAmount:num(p.standardAmount),lineDiscountType:p.lineDiscountType||'',lineDiscountValue:num(p.lineDiscountValue),lineDiscountAmount:num(p.lineDiscountAmount),promoDiscountAmount:num(p.promoDiscountAmount),headerDiscountAmount:num(p.headerDiscountAmount),discountAmount:num(p.discountAmount),finalAmount:num(p.finalAmount),amountExclTax:num(p.amountExclTax),taxAmount:num(p.taxAmount),lineLevel:p.lineLevel||'NORMAL',isBom:!!(p.isBundle||p.isBom),isGift:!!p.isGift,bomVersion:p.bomVersion||null,bomGroupNo:p.bomGroupNo||null,componentQty:num(p.componentQty),children:Array.isArray(p.children)?p.children:[]}}
function canPickProduct(row){return !row.isGift&&row.lineLevel!=='CHILD'}
function canEditQty(row){return !row.isGift&&row.lineLevel!=='CHILD'}
function canEditDiscount(row){return !row.isGift&&row.lineLevel!=='PARENT'}
function childSum(row,key){return (row.children||[]).reduce((s,c)=>s+num(c[key]),0)}
function unitPrice(row){if(row.lineLevel==='PARENT'){const qty=Math.max(num(row.qty),1);return childSum(row,'standardAmount')/qty}return num(row.standardPriceInclTax)}
function standardAmount(row){return row.lineLevel==='PARENT'?childSum(row,'standardAmount'):num(row.standardAmount)}
function canDeleteRow(row){return !row.isGift&&row.lineLevel!=='CHILD'}
function addLine(){if(!form.dealerId)return ElMessage.warning('请先选择经销商');form.lines.push(makeLine())}
function removeLine(i){form.lines.splice(i,1);schedulePreview()}
async function onProductPicked(row,picked){
  const p=picked.raw||picked.row||picked
  Object.assign(row,makeLine({...p,qty:row.qty||1,tempId:row.tempId,isBom:!!(p.is_bom||p.isBom||p.isBundle)}))
  const res=await request({url:`/api/product-bundles/product/${p.id}/active`}).catch(()=>null)
  const b=res?.data?.data ?? res?.data
  const nowActive=!b?.validFrom&&!b?.validTo?true:(()=>{const now=new Date();const from=b.validFrom?new Date(b.validFrom):null;const to=b.validTo?new Date(b.validTo):null;return (!from||from<=now)&&(!to||to>=now)})()
  if(!b?.lines?.length||b.versionStatus!=='active'||!nowActive){row.lineLevel='NORMAL';row.children=[];row.isBom=false;await loadPrice(row);schedulePreview();return}
  row.lineLevel='PARENT';row.isBom=true;row.bomVersion=b.bomVersion;row.standardPriceInclTax=0;row.standardAmount=0;row.finalAmount=0
  row.children=b.lines.filter(c=>String(c.childProductId)!==String(p.id)).map(c=>({...makeLine({id:c.childProductId,code:c.childProductCode,nameCn:c.childProductName,spec:c.childProductSpec}),qty:num(c.quantity)*num(row.qty),componentQty:num(c.quantity),lineLevel:'CHILD'}))
  await Promise.all(row.children.map(c=>loadPrice(c,p.id)))
  schedulePreview()
}
function activeNow(p){if(!p)return false;if(String(p.status||'').toLowerCase()!=='active')return false;const now=Date.now();const from=p.validFrom?new Date(p.validFrom).getTime():null;const to=p.validTo?new Date(p.validTo).getTime():null;if(from&&from>now)return false;if(to&&to<now)return false;return true}
function pickPrice(dealerRows,globalRows){const d=(dealerRows||[]).filter(activeNow).find(p=>String(p.partnerId||'')===String(form.dealerId||''));if(d)return d;const g=(globalRows||[]).filter(activeNow);return g.find(p=>Number(p.partnerId)===0)||g.find(p=>p.partnerId==null||p.partnerId==='')||null}
async function loadPrice(r,bomParentProductId){if(!form.dealerId||!r.productId)return;const base={productId:r.productId,priceScope:'SALE',priceContext:bomParentProductId?'BOM_COMPONENT':'STANDALONE',includeComponents:true,size:100};const params=bomParentProductId?{...base,partnerType:'DEALER',partnerId:form.dealerId,bomParentProductId}:{...base,partnerType:'DEALER',partnerId:form.dealerId,priceContext:'STANDALONE'};const res=await request({url:'/api/product-prices',params}).catch(()=>null);const list=(res)=>res?.data?.list||res?.data?.records||(Array.isArray(res?.data)?res.data:[]);const rows=list(res);const p=bomParentProductId?rows.find(x=>String(x.productId)===String(r.productId)&&String(x.bomParentProductId||'')===String(bomParentProductId)):pickPrice(rows.filter(x=>x.priceContext==='STANDALONE'),[]);if(p){r.standardPriceInclTax=num(p.salesPrice);r.taxRate=num(p.taxRate);r.priceResolved=num(p.salesPrice)>0}else{r.standardPriceInclTax=0;r.taxRate=0;r.priceResolved=false}}
function onLineQtyChange(row){if(row.children)row.children.forEach(c=>{c.qty=num(row.qty)*num(c.componentQty)});schedulePreview()}
function buildPreviewPayload(applyPromotions){return {applyPromotions:!!applyPromotions,orderType:form.orderType,dealerId:form.dealerId,expectedDate:form.expectedDate||null,headerDiscountType:form.headerDiscountType||null,headerDiscountValue:form.headerDiscountType?num(form.headerDiscountValue):null,lines:editableRoots.value.map(l=>({productId:l.productId,qty:num(l.qty),lineDiscountType:l.lineDiscountType||null,lineDiscountValue:l.lineDiscountType?num(l.lineDiscountValue):null,bomVersion:l.bomVersion||null,bomGroupNo:l.bomGroupNo||null,childDiscounts:(l.children||[]).filter(c=>c.lineDiscountType).map(c=>({productId:c.productId,lineDiscountType:c.lineDiscountType||null,lineDiscountValue:c.lineDiscountType?num(c.lineDiscountValue):0}))}))}}
function mapPreviewLine(l,current){return {...makeLine({...l,qty:num(l.qty)}),tempId:current?.tempId||'t'+(seq++),productLabel:`${l.productCode||''} ${l.productName||''}`.trim(),lineLevel:l.lineLevel||'NORMAL',isGift:(l.isGift===true||l.gift===true),isBom:l.lineLevel==='PARENT',bomVersion:l.bomVersion||current?.bomVersion||null,bomGroupNo:l.bomGroupNo||current?.bomGroupNo||null,componentQty:num(l.componentQty),children:[]}}
function applyPreview(data,fullRefresh){
  const existingGifts=fullRefresh?[]:flatLines.value.filter(l=>l.isGift).map(l=>({...l}));
  const roots=editableRoots.value;let rootIndex=0;let currentParent=null;const next=[];
  (data.lines||[]).forEach(l=>{
    if(l.isGift||l.gift){next.push(mapPreviewLine(l));return}
    const row=mapPreviewLine(l,l.lineLevel==='CHILD'?null:roots[rootIndex++]);
    if(l.lineLevel==='PARENT'){currentParent=row;next.push(row)}
    else if(l.lineLevel==='CHILD'&&currentParent&&l.bomGroupNo===currentParent.bomGroupNo){currentParent.children.push(row)}
    else{currentParent=null;next.push(row)}
  });
  next.push(...existingGifts);
  form.lines=next;
  form.amountInclTax=num(data.amountInclTax);form.discountAmount=num(data.discountAmount);form.finalAmount=num(data.finalAmount);
  form.taxAmount=num(data.taxAmount);form.amountExclTax=num(data.amountExclTax);
  if(fullRefresh)promoMessages.value=Array.isArray(data.promotionMessages)?data.promotionMessages:[];
}
async function executePreview(applyPromotions){
  if(!form.dealerId){form.finalAmount=0;return null}
  const token=++previewToken;
  if(applyPromotions)refreshing.value=true;else previewLoading.value=true;
  try{
    const {data}=await request({url:'/api/sales-orders/preview',method:'post',data:buildPreviewPayload(applyPromotions)});
    if(token===previewToken){applyPreview(data,applyPromotions);return data}
    return null;
  }catch(e){if(token===previewToken)error.value=e.response?.data?.message||e.message||'';return null}
  finally{if(token===previewToken){refreshing.value=false;previewLoading.value=false}}
}
function runPreview(){return executePreview(false)}
function refreshPromotions(){return executePreview(true)}
function schedulePreview(){clearTimeout(previewTimer);previewTimer=setTimeout(runPreview,300)}
async function onSave(submit){
  if(saving.value||submitting.value){ElMessage.warning('请求正在处理中，请勿重复提交');return}
  const ok=await formRef.value.validate().catch(()=>false);if(!ok)return
  if(!editableRoots.value.length)return ElMessage.error('请至少添加一行')
  if(editableRoots.value.some(l=>!l.productId||!Number.isInteger(num(l.qty))||num(l.qty)<=0))return ElMessage.error('请完善产品和整数数量')
  await refreshPromotions().catch(()=>null)
  const chargeable=flatLines.value.filter(l=>!l.isGift&&l.lineLevel!=='PARENT')
  const missing=chargeable.filter(l=>!num(l.standardPriceInclTax))
  if(missing.length){const names=missing.map(l=>l.productCode||l.productName||`#${l.productId}`).join('、');return ElMessage.error(`以下产品没有有效销售价格，请先在产品价格中维护：${names}`)}
  saving.value=!submit;submitting.value=submit;error.value=''
  try{
    const payload=buildPreviewPayload(true);payload.remark=form.remark
    const res=isEdit.value?await request({url:`/api/sales-orders/${form.id}`,method:'put',data:payload}):await request({url:'/api/sales-orders',method:'post',data:payload})
    if(submit)await request({url:`/api/sales-orders/${form.id||res.data.id}/submit`,method:'post'})
    ElMessage.success(submit?'销售订单已提交':'销售订单已保存');router.push('/m/orders')
  }catch(e){error.value=e.response?.data?.message||e.message||'保存失败'}
  finally{saving.value=false;submitting.value=false}
}
async function loadOrder(){
  const d=(await request({url:`/api/sales-orders/${route.params.id}`})).data
  Object.assign(form,{id:d.id,code:d.code,status:d.status||'DRAFT',dealerId:d.dealerId,orderType:d.orderType||'SALES',expectedDate:d.expectedDate||'',remark:d.remark||'',headerDiscountType:d.headerDiscountType||'',headerDiscountValue:d.headerDiscountValue||0,amountInclTax:num(d.amountInclTax),discountAmount:num(d.discountAmount),finalAmount:num(d.finalAmount),taxAmount:num(d.taxAmount),amountExclTax:num(d.amountExclTax)})
  if(Array.isArray(d.lines)){form.lines=d.lines.filter(l=>!l.bomParentLineId).map(l=>({...makeLine(l),productLabel:`${l.productCode||''} ${l.productName||''}`,qty:num(l.qty),standardPriceInclTax:num(l.standardPriceInclTax??l.unitPrice),lineLevel:l.lineLevel||(l.isGroupHeader?'PARENT':'NORMAL'),children:d.lines.filter(c=>String(c.bomParentLineId)===String(l.id)).map(c=>({...makeLine(c),productLabel:`${c.productCode||''} ${c.productName||''}`,qty:num(c.qty),standardPriceInclTax:num(c.standardPriceInclTax??c.unitPrice),lineLevel:'CHILD'}))}))}
}
function resetForm(){Object.assign(form,{id:null,code:'',status:'DRAFT',dealerId:null,orderType:'SALES',expectedDate:todayStr(),headerDiscountType:'',headerDiscountValue:0,remark:'',lines:[],amountInclTax:0,discountAmount:0,finalAmount:0,taxAmount:0,amountExclTax:0});saving.value=false;submitting.value=false;error.value='';nextTick(()=>{formRef.value?.clearValidate?.();formRef.value?.resetFields?.()})}
function handleRouteChange(){if(isEdit.value){loadOrder()}else{resetForm()}}
onMounted(handleRouteChange)
onActivated(handleRouteChange)
watch(()=>route.params.id,handleRouteChange)
</script>
<style scoped>
.order-create-page .area-scroll{padding:0}.form-container{padding:16px}.page-header{display:flex;align-items:center;justify-content:space-between;padding:14px 20px;background:#fff;border-bottom:1px solid #ebeef5}.page-title{display:flex;align-items:center;gap:10px}.page-title h3{margin:0;font-size:18px}.page-alert{margin:12px 16px 0}.form-container{padding:16px;display:flex;flex-direction:column;gap:12px}.lines-header{display:flex;justify-content:space-between;align-items:center}.lines-header>div{display:flex;gap:8px;align-items:center}.product-cell{display:flex;gap:6px;align-items:center}.child-product-text{display:flex;gap:8px;align-items:center;min-width:0}.child-product-code{color:#606266;font-weight:600;flex:0 0 auto}.child-product-name{color:#303133;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.discount-input{display:flex;gap:8px;width:100%;max-width:520px;align-items:center}.discount-input .el-select{width:140px;flex:0 0 140px}.discount-input .el-input-number{flex:1 1 auto;width:auto}.line-discount{display:flex;gap:4px}.muted{color:#909399}
</style>



