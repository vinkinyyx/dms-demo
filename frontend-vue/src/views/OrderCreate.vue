<template>
  <div class="area-page order-create-page">
    <div class="page-header">
      <div class="page-title">
        <el-button text @click="goBack"><el-icon><ArrowLeft/></el-icon></el-button>
        <h3>{{ isEdit ? '编辑销售订单' : '新增销售订单' }}</h3>
      </div>
      <div class="page-actions">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave(false)">保存草稿</el-button>
        <el-button v-if="canEdit" type="success" :loading="submitting" @click="onSave(true)">提交</el-button>
      </div>
    </div>
    <div class="area-scroll">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="page-alert"/>
      <el-alert v-for="(m,i) in promoMessages" :key="'p'+i" :title="m" type="success" show-icon :closable class="page-alert promo-alert"/>

      <div class="form-container">
        <el-card shadow="never">
          <template #header>基本信息</template>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
            <el-row :gutter="20">
              <el-col :xs="24" :sm="24" :md="12" :lg="12">
                <el-form-item label="经销商" prop="dealerId">
                  <ResourcePicker resource="dealers" v-model="form.dealerId" :display-value="form.dealerName" placeholder="选择经销商" @pick="onDealerPicked"/>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="12" :lg="12">
                <el-form-item label="订单类型">
                    <el-select v-model="form.orderType" style="width:100%" @change="onOrderTypeChange">
                    <el-option label="销售订单" value="SALES"/>
                    <el-option label="补货订单（寄售）" value="REPLENISHMENT" :disabled="!dealerConsignment"/>
                    <el-option label="开票订单（寄售）" value="INVOICE" :disabled="!dealerConsignment"/>
                    <el-option label="样品订单" value="SAMPLE"/>
                    <el-option label="定制订单" value="CUSTOM"/>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="24" :md="12" :lg="12">
                <el-form-item label="送货地址" prop="shipAddressId" required>
                  <el-select v-model="form.shipAddressId" placeholder="选择该客户的收货地址（必选）" clearable filterable style="width:100%" :loading="addressLoading">
                    <el-option v-for="a in addresses" :key="a.id" :value="a.id" :label="addressLabel(a)"/>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="12" :lg="12">
                <el-form-item label="期望日期">
                  <el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/>
                </el-form-item>
              </el-col>
              <el-col :span="24" v-if="form.orderType==='INVOICE'">
                <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px"
                  title="开票订单针对该经销商寄售库存开票；按合同价/客户价/全局折扣重新计价，不参与满减/满赠，不可用代金券/一口价/0金额。"/>
                <el-form-item label="结算终端" prop="terminalHospitalId">
                  <ResourcePicker resource="dealers" v-model="form.terminalHospitalId" :display-value="form.terminalHospitalName" placeholder="选择终端医院（开票结算对象，可搜索）" @pick="onTerminalPicked"/>
                </el-form-item>
              </el-col>
              <el-col :span="24" v-if="form.orderType==='SAMPLE'">
                <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px" title="样品订单仅可下一个单品，订单0金额，不参与折扣与促销。"/>
                <el-form-item label="申请样品原因" prop="sampleReason">
                  <el-input v-model="form.sampleReason" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="请填写申请样品原因（必填）"/>
                </el-form-item>
              </el-col>
              <el-col :span="24" v-if="form.orderType==='REPLENISHMENT'">
                <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px" title="补货订单产品均为0金额，不使用折扣、不参与满减/满赠；发货后厂家库存扣减并计入该经销商寄售库存，供日后开票。"/>
              </el-col>
              <el-col :span="24">
                <el-form-item label="备注">
                  <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit/>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-card>

        <el-card shadow="never">
          <template #header>
            <div class="lines-header">
              <span>订单明细</span>
              <div>
                <el-tag size="small" type="info">共 {{ editableRoots.length }} 个录入行</el-tag>
                <el-tag v-if="giftLines.length" size="small" type="warning">赠品 {{ giftLines.length }} 行</el-tag>
                <el-tag size="small" type="success">应付 ¥{{ payableTotal.toFixed(2) }}</el-tag>
                <el-tooltip v-if="form.orderType==='INVOICE' && !form.dealerId" content="请先选择经销商后再拣选寄售库存" placement="top">
                  <el-button type="warning" size="small" :icon="Box" disabled>选择寄售库存</el-button>
                </el-tooltip>
                <el-button v-else-if="form.orderType==='INVOICE'" type="warning" size="small" :icon="Box" @click="openConsignmentPicker">选择寄售库存</el-button>
                <el-button v-else type="primary" size="small" :icon="Plus" @click="addLine">添加行</el-button>
                <el-button v-if="form.orderType!=='INVOICE'" size="small" :icon="Refresh" :loading="refreshing" @click="refreshPromotions">刷新赠品及价格</el-button>
              </div>
            </div>
          </template>
          <el-table :data="form.lines" border stripe size="small" row-key="tempId" :tree-props="{children:'children'}" :default-expand-all="true">
            <el-table-column label="产品" min-width="240">
              <template #default="{row}">
                <div class="product-cell">
                  <ResourcePicker v-if="form.orderType==='INVOICE'" resource="products" disabled :model-value="row.productId" :display-value="row.productLabel" placeholder="请点击「选择寄售库存」" style="flex:1"/>
                  <ResourcePicker v-else-if="canPickProduct(row)" resource="products" v-model="row.productId" :display-value="row.productLabel" @pick="p=>onProductPicked(row,p)" style="flex:1"/>
                  <el-tag v-if="form.orderType==='INVOICE' && (row.batchNo||row.serialNo)" size="small" type="warning" style="flex:0 0 auto">批号 {{row.batchNo||'-'}} / 序列号 {{row.serialNo||'-'}}</el-tag>
                  <span v-else class="child-product-text"><span class="child-product-code">{{row.productCode}}</span><span class="child-product-name">{{row.productName}}</span></span>
                  <el-tag v-if="row.lineLevel==='PARENT'||row.isBom" size="small" type="warning">BOM母件</el-tag>
                  <el-tag v-if="row.isGift" size="small" type="danger">赠品</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="productSpec" label="规格" width="120" show-overflow-tooltip/>
            <el-table-column prop="unit" label="单位" width="60" align="center"/>
            <el-table-column label="数量" width="110">
              <template #default="{row}">
                <el-input-number v-model="row.qty" :min="1" :precision="0" :step="1" controls-position="right" size="small" style="width:100%" :disabled="!canEditQty(row)" @change="()=>onLineQtyChange(row)"/>
              </template>
            </el-table-column>
            <el-table-column label="基础单价" width="135" align="right">
              <template #default="{row}">
                <div class="price-cell">
                  <span>{{ basePrice(row).toFixed(2) }}</span>
                  <el-tag v-if="row.priceSource && row.lineLevel!=='PARENT'" size="small" :type="sourceTagType(row.priceSource)">{{ sourceLabel(row.priceSource) }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="产品折扣" width="90" align="right">
              <template #default="{row}">
                <span :class="{muted:!num(row.productDiscountAmount)}">-{{ num(row.productDiscountAmount).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="促销" width="125">
              <template #default="{row}">
                <el-tag v-if="row.isGift" size="small" type="danger">促销赠品</el-tag>
                <el-tag v-else-if="row.promoType" size="small" type="success">{{ promoTypeLabel(row.promoType) }} -{{ num(row.promoDiscountAmount).toFixed(2) }}</el-tag>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="行手动折扣" width="240">
              <template #default="{row}">
                <div v-if="canEditDiscount(row)" class="line-discount">
                  <el-select v-model="row.lineDiscountType" size="small" clearable style="width:76px" :disabled="discountLocked(row)" placeholder="类型">
                    <el-option label="比例" value="PERCENT"/>
                    <el-option label="金额" value="AMOUNT"/>
                  </el-select>
                  <el-select v-model="row.lineDiscountDirection" size="small" style="width:72px" :disabled="discountLocked(row)||!row.lineDiscountType">
                    <el-option label="折扣" value="REDUCE"/>
                    <el-option label="加价" value="ADD"/>
                  </el-select>
                  <el-input-number v-if="row.lineDiscountType" v-model="row.lineDiscountValue" :min="0" :precision="2" :max="row.lineDiscountType==='PERCENT'?100:undefined" :controls="false" size="small" style="width:82px" :disabled="discountLocked(row)" @change="schedulePreview"/>
                </div>
                <span v-else class="muted">{{ row.isGift ? '赠品不收费' : (row.lineLevel==='PARENT' ? '母件不打折' : '-') }}</span>
              </template>
            </el-table-column>
            <el-table-column label="行0金额" width="70" align="center">
              <template #default="{row}">
                <el-switch v-if="canZeroRow(row)" v-model="row.lineZero" :disabled="zeroLocked(row)" @change="onLineZeroChange(row)"/>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="行金额" width="105" align="right">
              <template #default="{row}"><b>{{ lineFinal(row).toFixed(2) }}</b></template>
            </el-table-column>
            <el-table-column label="EA单价" width="100" align="right">
              <template #default="{row}">
                <span v-if="row.isGift||row.lineLevel==='PARENT'" class="muted">0.00</span>
                <span v-else>{{ num(row.unitPriceInclTax).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60" fixed="right">
              <template #default="{ $index, row }">
                <el-button v-if="canDeleteRow(row)" type="danger" link size="small" @click="removeLine($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never">
          <template #header><span>结算与计价</span></template>
          <el-row :gutter="20">
            <el-col :xs="24" :md="12">
              <div class="mode-block">
                <div class="block-title">计价方式（互斥）</div>
                <el-radio-group v-model="form.pricingMode" :disabled="!canEdit || pricingLocked" @change="onModeChange">
                  <el-radio-button label="NORMAL">普通折扣</el-radio-button>
                  <el-radio-button label="FIXED_PRICE">整单一口价</el-radio-button>
                  <el-radio-button label="ZERO_ORDER">整单0金额</el-radio-button>
                  <el-radio-button label="VOUCHER">代金券</el-radio-button>
                </el-radio-group>
                <div v-if="form.pricingMode==='FIXED_PRICE'" class="mode-input">
                  <span>整单成交价（含税）：</span>
                  <el-input-number v-model="form.fixedPrice" :min="0" :precision="2" controls-position="right" style="width:180px" @change="schedulePreview"/>
                </div>
                <div v-if="form.pricingMode==='VOUCHER'" class="mode-input">
                  <el-select v-model="form.voucherId" placeholder="选择可用代金券" clearable filterable style="width:360px" :loading="voucherLoading" @change="schedulePreview">
                    <el-option v-for="v in vouchers" :key="v.id" :value="v.id" :label="voucherLabel(v)"/>
                  </el-select>
                  <span v-if="!vouchers.length && form.dealerId" class="muted small">该客户当前无可用代金券</span>
                </div>
                <el-alert v-if="form.pricingMode!=='NORMAL'" type="warning" :closable="false" show-icon class="mode-tip"
                  title="一口价 / 整单0金额 / 代金券模式下，产品折扣、促销、行折扣、客户折扣、整单折扣与行0金额均不可用，三种方式两两互斥。"/>
              </div>

              <div v-if="form.pricingMode==='NORMAL'" class="mode-block">
                <div class="block-title">整单手动折扣</div>
                <div class="discount-input">
                  <el-select v-model="form.headerDiscountType" clearable class="discount-type" placeholder="折扣类型">
                    <el-option label="百分比" value="PERCENT"/>
                    <el-option label="固定金额" value="AMOUNT"/>
                  </el-select>
                  <el-select v-model="form.headerDiscountDirection" class="discount-type" :disabled="!form.headerDiscountType">
                    <el-option label="折扣(减)" value="REDUCE"/>
                    <el-option label="加价(高开)" value="ADD"/>
                  </el-select>
                  <el-input-number v-if="form.headerDiscountType" v-model="form.headerDiscountValue" :min="0" :precision="2" :max="form.headerDiscountType==='PERCENT'?100:undefined" controls-position="right" class="discount-value" @change="schedulePreview"/>
                </div>
                <div class="muted small">加价（高开）无上限；折扣（减）分摊后任一行不能小于 0。</div>
              </div>
            </el-col>

            <el-col :xs="24" :md="12">
              <div class="settle-block" v-loading="previewLoading">
                <div class="settle-row"><span>原价合计</span><span>¥{{ num(form.amountInclTax).toFixed(2) }}</span></div>
                <div class="settle-row"><span>产品折扣</span><span>-¥{{ num(summary.productDiscountTotal).toFixed(2) }}</span></div>
                <div class="settle-row"><span>促销优惠</span><span>-¥{{ num(summary.promoDiscountTotal).toFixed(2) }}</span></div>
                <div class="settle-row"><span>行手动折扣</span><span class="signed">{{ signedText(summary.lineDiscountTotal) }}</span></div>
                <div class="settle-row"><span>客户折扣</span><span>-¥{{ num(summary.dealerDiscountTotal).toFixed(2) }}</span></div>
                <div class="settle-row"><span>整单折扣</span><span class="signed">{{ signedText(summary.headerDiscountTotal) }}</span></div>
                <el-divider style="margin:8px 0"/>
                <div class="settle-row total"><span>整单金额</span><span>¥{{ num(form.finalAmount).toFixed(2) }}</span></div>
                <div v-if="form.pricingMode==='VOUCHER'" class="settle-row voucher"><span>代金券抵扣</span><span>-¥{{ num(summary.voucherAmount).toFixed(2) }}</span></div>
                <div class="settle-row payable"><span>实付金额</span><span>¥{{ payableTotal.toFixed(2) }}</span></div>
                <div class="settle-row muted"><span>其中税额</span><span>¥{{ num(form.taxAmount).toFixed(2) }}</span></div>
                <div class="settle-row muted"><span>不含税金额</span><span>¥{{ num(form.amountExclTax).toFixed(2) }}</span></div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </div>

    <el-dialog v-model="consignDialog.visible" title="选择经销商寄售库存" width="1080px" append-to-body destroy-on-close>
      <div style="margin-bottom:10px;display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <el-tag type="info" size="large">经销商：{{ form.dealerName || '-' }}</el-tag>
        <el-input v-model="consignDialog.keyword" placeholder="产品编码/名称/批号/序列号" clearable style="width:240px" @keyup.enter="loadConsignment"/>
        <el-select v-model="consignDialog.warehouseId" placeholder="全部仓库" clearable filterable style="width:160px" @change="onConsignFilterChange">
          <el-option v-for="w in consignDialog.warehouses" :key="w.id" :value="w.id" :label="w.name"/>
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadConsignment">查询</el-button>
        <el-button :icon="RefreshLeft" @click="resetConsignFilter">重置</el-button>
        <span class="muted small">仅显示该经销商当前可用（在库-已锁定）寄售库存；点击整行即可勾选，设置本次开票数量后加入明细。序列号商品每行只能开 1 件。</span>
      </div>
      <el-table :data="consignFilteredRows" v-loading="consignDialog.loading" border stripe size="small" max-height="430" row-key="ck"
                @selection-change="onConsignSelect" @row-click="onConsignRowClick" ref="consignTableRef"
                :row-class-name="consignRowClass">
        <el-table-column type="selection" width="42" :reserve-selection="true" :selectable="consignSelectable"/>
        <el-table-column label="产品编码" prop="productCode" width="120"/>
        <el-table-column label="产品名称" prop="productName" min-width="160" show-overflow-tooltip/>
        <el-table-column label="规格" prop="productSpec" width="110" show-overflow-tooltip/>
        <el-table-column label="批号" prop="batchNo" width="110"/>
        <el-table-column label="序列号" prop="serialNo" width="120"/>
        <el-table-column label="仓库" prop="warehouseName" width="90"/>
        <el-table-column label="在库" prop="onHandQty" width="70" align="right"/>
        <el-table-column label="已锁定" prop="lockedQty" width="70" align="right">
          <template #default="{row}">
            <span :style="{color:num(row.lockedQty)>0?'var(--el-color-warning)':'#909399'}">{{ num(row.lockedQty) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可用量" prop="availableQty" width="70" align="right"/>
        <el-table-column label="标准单价" width="100" align="right">
          <template #default="{row}">{{ num(row.stdUnitPrice).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="本次开票数量" width="140" align="center">
          <template #default="{row}">
            <el-input-number v-if="!isSerialRow(row)" v-model="row.pickQty" :min="1" :max="num(row.availableQty)" :precision="0" controls-position="right" size="small" style="width:120px"/>
            <el-tag v-else type="info" size="small">序列号商品 1 件</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:8px;display:flex;justify-content:space-between;align-items:center">
        <span class="muted small">已选 {{ consignDialog.selected.length }} 行，合计开票数量 {{ consignPickTotal }}。</span>
        <b style="color:var(--el-color-danger)">合计开票金额：¥{{ consignPickAmount.toFixed(2) }}</b>
      </div>
      <template #footer>
        <el-button @click="consignDialog.visible=false">取消</el-button>
        <el-button type="primary" @click="confirmConsignment">加入开票明细（{{ consignDialog.selected.length }} 行）</el-button>
      </template>
    </el-dialog>
    </div>
  </div>
</template><script setup>
defineOptions({ name: 'OrderCreate' })
import {computed,nextTick,onMounted,onActivated,reactive,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {ArrowLeft,Plus,Refresh,Box,Search,RefreshLeft} from '@element-plus/icons-vue'
import ResourcePicker from '@/components/ResourcePicker.vue'
import request from '@/utils/request'
import {calcPreview,availableVouchers,dealerAddresses} from '@/api/orderPricing'
const route=useRoute(),router=useRouter(),isEdit=computed(()=>!!route.params.id),formRef=ref(null),saving=ref(false),submitting=ref(false),previewLoading=ref(false),refreshing=ref(false),error=ref(''),promoMessages=ref([])
const addresses=ref([]),addressLoading=ref(false),vouchers=ref([]),voucherLoading=ref(false)
let previewTimer=null
let previewToken=0
let seq=1
const form=reactive({id:null,status:'DRAFT',dealerId:null,dealerName:'',orderType:'SALES',expectedDate:'',shipAddressId:null,terminalHospitalId:null,terminalHospitalName:'',sampleReason:'',remark:'',pricingMode:'NORMAL',fixedPrice:null,voucherId:null,headerDiscountType:'',headerDiscountDirection:'REDUCE',headerDiscountValue:0,lines:[],amountInclTax:0,discountAmount:0,finalAmount:0,taxAmount:0,amountExclTax:0})
const summary=reactive({productDiscountTotal:0,promoDiscountTotal:0,lineDiscountTotal:0,dealerDiscountTotal:0,headerDiscountTotal:0,voucherAmount:0,payableAmount:0})
const rules={dealerId:[{required:true,message:'请选择经销商',trigger:'change'}],shipAddressId:[{required:true,message:'请选择送货地址',trigger:'change'}],sampleReason:[{required:true,message:'请填写申请样品原因',trigger:'blur'}],terminalHospitalId:[{required:true,message:'开票订单请选择结算终端医院',trigger:'change'}]}
const canEdit=computed(()=>!isEdit.value||['DRAFT','REJECTED'].includes(form.status))
const flatLines=computed(()=>flatten(form.lines))
const editableRoots=computed(()=>form.lines.filter(l=>!l.isGift&&l.lineLevel!=='CHILD'))
const giftLines=computed(()=>flatLines.value.filter(l=>l.isGift))
const payableTotal=computed(()=>{const v=num(summary.payableAmount);return v>0?v:Math.max(num(form.finalAmount)-num(summary.voucherAmount),0)})
const num=v=>Number(v||0)
function flatten(a){return a.flatMap(x=>[x,...(Array.isArray(x.children)?flatten(x.children):[])])}
function goBack(){router.push('/m/orders')}
function todayStr(){const d=new Date();const mm=String(d.getMonth()+1).padStart(2,'0');const dd=String(d.getDate()).padStart(2,'0');return `${d.getFullYear()}-${mm}-${dd}`}
function sourceLabel(s){return ({CONTRACT:'合同价',DEALER:'客户价',GLOBAL:'全局价'})[s]||s||''}
function sourceTagType(s){return ({CONTRACT:'danger',DEALER:'primary',GLOBAL:'info'})[s]||'info'}
function promoTypeLabel(t){return ({QTY_DISCOUNT:'满件折扣',QTY_REDUCE:'满件减',GIFT:'满赠'})[t]||t||'促销'}
function addressLabel(a){const region=[a.province,a.city,a.district].filter(Boolean).join('');const name=a.addressName?`【${a.addressName}】`:'';const contact=a.contactName?` ${a.contactName}${a.phone?('/'+a.phone):''}`:'';return `${name}${region}${a.address||''}${contact}`}
function voucherLabel(v){const scope=({ALL:'全场',PRODUCT:'指定产品',CATEGORY:'指定品类'})[v.scopeType]||'';const to=v.validTo?String(v.validTo).slice(0,10):'';return `${v.name||v.code} 面值¥${num(v.faceValue).toFixed(2)}${v.minSpend?` 满¥${num(v.minSpend).toFixed(2)}可用`:''} ${scope}${to?(' 有效期至'+to):''}`}
function signedText(v){const n=num(v);if(n>0)return `+¥${n.toFixed(2)}`;return `-¥${Math.abs(n).toFixed(2)}`}
function basePrice(row){if(row.lineLevel==='PARENT'){const qty=Math.max(num(row.qty),1);return childSum(row,'standardAmount')/qty}return num(row.basePriceInclTax??row.standardPriceInclTax)}
function lineFinal(row){if(row.lineLevel==='PARENT')return childSum(row,'finalAmount');return num(row.finalAmount)}
function childSum(row,key){return (row.children||[]).reduce((s,c)=>s+num(c[key]),0)}
function isExclusiveMode(){return form.pricingMode&&form.pricingMode!=='NORMAL'}
const pricingLocked=computed(()=>['INVOICE','REPLENISHMENT','SAMPLE','CUSTOM'].includes(form.orderType))
function discountLocked(row){if(isExclusiveMode())return true;return row.lineLevel!=='CHILD'&&row.promoType==='QTY_DISCOUNT'}
function zeroLocked(row){if(isExclusiveMode())return true;return !!row.promoType}
function canZeroRow(row){return !row.isGift&&row.lineLevel!=='PARENT'&&row.lineLevel!=='CHILD'}
function onLineZeroChange(row){if(row.lineZero){row.lineDiscountType='';row.lineDiscountValue=0}schedulePreview()}
function onModeChange(mode){if(mode==='VOUCHER'){loadVouchers()}if(mode!=='NORMAL'){form.lines.forEach(l=>{l.lineZero=false});form.headerDiscountType='';form.headerDiscountValue=0}if(mode!=='VOUCHER'){form.voucherId=null}if(mode!=='FIXED_PRICE'){form.fixedPrice=null}schedulePreview()}
function makeLine(p={}){const linePid=p.productId??p.id??null;return {tempId:'t'+(seq++),productId:linePid,productCode:p.code||p.productCode||'',productName:p.nameCn||p.name||p.productName||'',productLabel:linePid?`${p.code||p.productCode||''} ${p.nameCn||p.name||p.productName||''}`.trim():'',productSpec:p.spec||p.productSpec||'',unit:p.unit||p.unitType||'EA',qty:Number(p.qty||1),standardPriceInclTax:num(p.currentPrice??p.price??p.standardPriceInclTax),basePriceInclTax:num(p.basePriceInclTax),priceSource:p.priceSource||'',taxRate:num(p.taxRate),standardAmount:num(p.standardAmount),productDiscountAmount:num(p.productDiscountAmount),lineDiscountType:p.lineDiscountType||'',lineDiscountDirection:p.lineDiscountDirection||'REDUCE',lineDiscountValue:num(p.lineDiscountValue),lineDiscountAmount:num(p.lineDiscountAmount),promoType:p.promoType||'',promotionId:p.promotionId||null,promoDiscountAmount:num(p.promoDiscountAmount),dealerDiscountAmount:num(p.dealerDiscountAmount),headerDiscountAmount:num(p.headerDiscountAmount),unitPriceInclTax:num(p.unitPriceInclTax),lineZero:!!p.lineZero,discountAmount:num(p.discountAmount),finalAmount:num(p.finalAmount),amountExclTax:num(p.amountExclTax),taxAmount:num(p.taxAmount),lineLevel:p.lineLevel||'NORMAL',isBom:!!(p.isBundle||p.isBom),isGift:!!(p.isGift||p.gift),bomVersion:p.bomVersion||null,bomGroupNo:p.bomGroupNo||null,componentQty:num(p.componentQty),children:Array.isArray(p.children)?p.children:[],batchNo:p.batchNo||'',serialNo:p.serialNo||'',stockId:p.stockId??p.consignmentStockId??null}}
function canPickProduct(row){return !row.isGift&&row.lineLevel!=='CHILD'}
function canEditQty(row){if(form.orderType==='INVOICE')return false;return !row.isGift&&row.lineLevel!=='CHILD'}
function canEditDiscount(row){return !row.isGift&&row.lineLevel!=='PARENT'}
function canDeleteRow(row){return !row.isGift&&row.lineLevel!=='CHILD'}
function addLine(){if(!form.dealerId)return ElMessage.warning('请先选择经销商');form.lines.push(makeLine())}
// ===== v4.4.0 开票订单：寄售库存选择（v4.4.1 弹窗交互加固 + stockId 精准锁定） =====
const consignDialog=reactive({visible:false,loading:false,keyword:'',warehouseId:null,warehouses:[],rows:[],selected:[]})
const consignTableRef=ref(null)
const consignPickTotal=computed(()=>consignDialog.selected.reduce((sum,r)=>sum+(isSerialRow(r)?1:num(r.pickQty)),0))
const consignPickAmount=computed(()=>consignDialog.selected.reduce((sum,r)=>sum+num(r.stdUnitPrice)*(isSerialRow(r)?1:num(r.pickQty)),0))
const consignFilteredRows=computed(()=>!consignDialog.warehouseId?consignDialog.rows:consignDialog.rows.filter(r=>String(r.warehouseId)===String(consignDialog.warehouseId)))
function isSerialRow(row){return !!(row?.serialNo)||row?.isSerialManaged===true}
function consignSelectable(row){return num(row.availableQty)>0}
function consignRowClass({row}){return consignDialog.selected.some(s=>s.stockId===row.stockId)?'consign-row-picked':''}
function onConsignRowClick(row,column,e){
  if(!consignSelectable(row)){ElMessage.warning('该台账行可用量为 0，已被其他开票单锁定或在库不足');return}
  const tag=(e?.target?.closest?.('input,button,.el-input-number,.el-checkbox,.el-input'))?.tagName
  if(tag)return
  if(!consignTableRef.value)return
  const checked=consignDialog.selected.some(s=>s.stockId===row.stockId)
  consignTableRef.value.toggleRowSelection(row,!checked)
}
function onConsignFilterChange(){/* 仓库筛选为前端 computed 过滤，change 仅触发响应式刷新 */}
async function resetConsignFilter(){consignDialog.keyword='';consignDialog.warehouseId=null;await loadConsignment()}
async function openConsignmentPicker(){
  if(!form.dealerId)return ElMessage.warning('请先选择经销商');
  consignDialog.visible=true;
  await loadConsignment();
  await nextTick();
  if(consignTableRef.value){consignTableRef.value.clearSelection();consignDialog.rows.forEach(r=>{if(consignDialog.selected.some(x=>x.stockId===r.stockId))consignTableRef.value.toggleRowSelection(r,true)})}
}
async function loadConsignment(){
  if(!form.dealerId)return;
  consignDialog.loading=true;
  try{
    const params={dealerId:form.dealerId};if(consignDialog.keyword)params.keyword=consignDialog.keyword;
    const res=await request({url:'/api/consignment/available',params});
    const list=res?.data||[];
    const prev=new Map(consignDialog.rows.map(r=>[r.stockId,r.pickQty]));
    consignDialog.rows=list.map(r=>({...r,ck:r.stockId,pickQty:prev.get(r.stockId)||1}));
    const whMap=new Map();
    list.forEach(r=>{if(r.warehouseId!=null&&!whMap.has(String(r.warehouseId)))whMap.set(String(r.warehouseId),{id:r.warehouseId,name:r.warehouseName||('仓库'+r.warehouseId)})});
    consignDialog.warehouses=[...whMap.values()];
    await nextTick();
    if(consignTableRef.value){consignTableRef.value.clearSelection();consignDialog.rows.forEach(r=>{if(consignDialog.selected.some(x=>x.stockId===r.stockId))consignTableRef.value.toggleRowSelection(r,true)})}
  }catch(e){ElMessage.error('加载寄售库存失败');consignDialog.rows=[];consignDialog.warehouses=[]}
  finally{consignDialog.loading=false}
}
function onConsignSelect(sel){consignDialog.selected=sel||[]}
function confirmConsignment(){
  if(!consignDialog.selected.length)return ElMessage.warning('请先勾选寄售库存行');
  const picked=consignDialog.selected.map(r=>({
    id:r.productId,productId:r.productId,code:r.productCode,nameCn:r.productName,spec:r.productSpec,
    qty:isSerialRow(r)?1:Math.min(Math.max(parseInt(num(r.pickQty))||1,1),num(r.availableQty)),
    batchNo:r.batchNo||'',serialNo:r.serialNo||'',stockId:r.stockId
  }));
  // v4.4.1 按台账行 stockId 精准合并（同产品不同批号/序列号/台账行各自独立成行，不做产品级合并）
  const map=new Map();
  for(const it of picked){if(map.has(it.stockId)){map.get(it.stockId).qty+=it.qty}else{map.set(it.stockId,{...it})}}
  form.lines=[...map.values()].map(it=>makeLine(it));
  consignDialog.visible=false;
  ElMessage.success('已加入 '+form.lines.length+' 行寄售库存开票明细');
  schedulePreview();
}
function removeLine(i){form.lines.splice(i,1);schedulePreview()}
async function onProductPicked(row,picked){
  const p=picked.raw||picked.row||picked
  Object.assign(row,makeLine({...p,qty:row.qty||1,tempId:row.tempId,isBom:!!(p.is_bom||p.isBom||p.isBundle)}))
  const res=await request({url:`/api/product-bundles/product/${p.id}/active`}).catch(()=>null)
  const b=res?.data?.data ?? res?.data
  const nowActive=!b?.validFrom&&!b?.validTo?true:(()=>{const now=new Date();const from=b.validFrom?new Date(b.validFrom):null;const to=b.validTo?new Date(b.validTo):null;return (!from||from<=now)&&(!to||to>=now)})()
  if(!b?.lines?.length||b.versionStatus!=='active'||!nowActive){row.lineLevel='NORMAL';row.children=[];row.isBom=false;await loadPrice(row);schedulePreview();return}
  row.lineLevel='PARENT';row.isBom=true;row.bomVersion=b.bomVersion;row.standardPriceInclTax=0;row.basePriceInclTax=0;row.standardAmount=0;row.finalAmount=0
  row.children=b.lines.filter(c=>String(c.childProductId)!==String(p.id)).map(c=>({...makeLine({id:c.childProductId,code:c.childProductCode,nameCn:c.childProductName,spec:c.childProductSpec}),qty:num(c.quantity)*num(row.qty),componentQty:num(c.quantity),lineLevel:'CHILD'}))
  await Promise.all(row.children.map(c=>loadPrice(c,p.id)))
  schedulePreview()
}
function activeNow(p){if(!p)return false;if(String(p.status||'').toLowerCase()!=='active')return false;const now=Date.now();const from=p.validFrom?new Date(p.validFrom).getTime():null;const to=p.validTo?new Date(p.validTo).getTime():null;if(from&&from>now)return false;if(to&&to<now)return false;return true}
function pickPrice(dealerRows,globalRows){const d=(dealerRows||[]).filter(activeNow).find(p=>String(p.partnerId||'')===String(form.dealerId||''));if(d)return d;const g=(globalRows||[]).filter(activeNow);return g.find(p=>Number(p.partnerId)===0)||g.find(p=>p.partnerId==null||p.partnerId==='')||null}
async function loadPrice(r,bomParentProductId){if(!form.dealerId||!r.productId)return;const base={productId:r.productId,priceScope:'SALE',priceContext:bomParentProductId?'BOM_COMPONENT':'STANDALONE',includeComponents:true,size:100};const params=bomParentProductId?{...base,partnerType:'DEALER',partnerId:form.dealerId,bomParentProductId}:{...base,partnerType:'DEALER',partnerId:form.dealerId,priceContext:'STANDALONE'};const res=await request({url:'/api/product-prices',params}).catch(()=>null);const list=(x)=>x?.data?.list||x?.data?.records||(Array.isArray(x?.data)?x.data:[]);const rows=list(res);const p=bomParentProductId?rows.find(x=>String(x.productId)===String(r.productId)&&String(x.bomParentProductId||'')===String(bomParentProductId)):pickPrice(rows.filter(x=>x.priceContext==='STANDALONE'),[]);if(p){r.standardPriceInclTax=num(p.salesPrice);r.basePriceInclTax=num(p.salesPrice);r.taxRate=num(p.taxRate);r.priceResolved=num(p.salesPrice)>0}else{r.standardPriceInclTax=0;r.basePriceInclTax=0;r.taxRate=0;r.priceResolved=false}}
function onLineQtyChange(row){if(row.children)row.children.forEach(c=>{c.qty=num(row.qty)*num(c.componentQty)});schedulePreview()}
const dealerConsignment = ref(false)
async function refreshDealerConsignment(){
  if(!form.dealerId){dealerConsignment.value=false;return}
  try{const res=await request({url:'/api/dealers/'+form.dealerId});const d=res?.data||res;dealerConsignment.value=!!(d.consignmentEnabled ?? d.consignment_enabled)}catch(e){dealerConsignment.value=false}
}
function onOrderTypeChange(t){
  if(['INVOICE','REPLENISHMENT','SAMPLE','CUSTOM'].includes(t)){form.pricingMode='NORMAL';form.voucherId=null;form.fixedPrice=null}
  if((t==='REPLENISHMENT'||t==='INVOICE')&&!dealerConsignment.value){ElMessage.warning('该经销商未开启寄售库存，不能下补货/开票订单');form.orderType='SALES'}
  if(t!=='INVOICE'){form.terminalHospitalId=null;form.terminalHospitalName=''}
  if(t!=='SAMPLE'){form.sampleReason=''}
  schedulePreview()
}
function onTerminalPicked(row){form.terminalHospitalId=row?.id??null;form.terminalHospitalName=row?((row.name||row.displayName||'')):''}
let prevDealerId=null
async function onDealerChange(){
  addresses.value=[];vouchers.value=[];form.shipAddressId=null;form.voucherId=null;form.lines=[];form.terminalHospitalId=null;form.terminalHospitalName='';
  consignDialog.selected=[];consignDialog.rows=[];consignDialog.warehouses=[];consignDialog.keyword='';consignDialog.warehouseId=null;
  await Promise.all([loadAddresses(),refreshDealerConsignment()]);
  prevDealerId=form.dealerId;
  schedulePreview()
}
async function onDealerPicked(p){
  const hasLines=editableRoots.value.some(l=>l.productId);
  if(!p||!p.value){
    if(prevDealerId==null){form.dealerName='';form.lines=[];addresses.value=[];vouchers.value=[];return}
    if(hasLines){try{await ElMessageBox.confirm('清空经销商将清空当前已录入的明细行，是否继续？','提示',{type:'warning',confirmButtonText:'清空',cancelButtonText:'取消'})}catch(e){form.dealerId=prevDealerId;return}}
    form.dealerId=null;form.dealerName='';form.lines=[];addresses.value=[];vouchers.value=[];form.terminalHospitalId=null;form.terminalHospitalName='';prevDealerId=null;schedulePreview();return
  }
  if(prevDealerId!=null&&String(p.value)!==String(prevDealerId)&&hasLines){
    try{await ElMessageBox.confirm('切换经销商将清空当前已录入的明细行（含寄售开票拣选），是否继续？','提示',{type:'warning',confirmButtonText:'切换',cancelButtonText:'取消'})}catch(e){form.dealerId=prevDealerId;return}
  }
  form.dealerName=p.label||p.row?.name||p.name||'';
  await onDealerChange()
}
async function loadAddresses(){if(!form.dealerId){addresses.value=[];return}addressLoading.value=true;try{const res=await dealerAddresses(form.dealerId);addresses.value=Array.isArray(res?.data)?res.data:(res?.data?.list||[])}catch(e){addresses.value=[]}finally{addressLoading.value=false}}
async function loadVouchers(){if(!form.dealerId){vouchers.value=[];return}voucherLoading.value=true;try{const productIds=editableRoots.value.map(l=>l.productId).filter(Boolean).join(',');const res=await availableVouchers({dealerId:form.dealerId,amount:num(form.amountInclTax)||undefined,productIds:productIds||undefined});vouchers.value=Array.isArray(res?.data)?res.data:[]}catch(e){vouchers.value=[]}finally{voucherLoading.value=false}}
function buildPreviewPayload(applyPromotions){
  const exclusive=isExclusiveMode();
  const payload={applyPromotions:!!applyPromotions,orderType:form.orderType,dealerId:form.dealerId,expectedDate:form.expectedDate||null,pricingMode:form.pricingMode||'NORMAL',lines:editableRoots.value.map(l=>({productId:l.productId,qty:num(l.qty),batchNo:l.batchNo||null,serialNo:l.serialNo||null,consignmentStockId:l.stockId??null,lineZero:!exclusive&&!!l.lineZero,lineDiscountType:(!exclusive&&l.lineDiscountType)?l.lineDiscountType:null,lineDiscountValue:(!exclusive&&l.lineDiscountType)?num(l.lineDiscountValue):null,lineDiscountDirection:(!exclusive&&l.lineDiscountType)?(l.lineDiscountDirection||'REDUCE'):null,bomVersion:l.bomVersion||null,bomGroupNo:l.bomGroupNo||null,childDiscounts:(l.children||[]).filter(c=>!exclusive&&c.lineDiscountType).map(c=>({productId:c.productId,lineDiscountType:c.lineDiscountType||null,lineDiscountValue:c.lineDiscountType?num(c.lineDiscountValue):0,lineDiscountDirection:c.lineDiscountDirection||'REDUCE'}))}))}
  if(!exclusive){payload.headerDiscountType=form.headerDiscountType||null;payload.headerDiscountValue=form.headerDiscountType?num(form.headerDiscountValue):null;payload.headerDiscountDirection=form.headerDiscountType?(form.headerDiscountDirection||'REDUCE'):null}
  else{payload.headerDiscountType=null;payload.headerDiscountValue=null}
  if(form.pricingMode==='FIXED_PRICE')payload.fixedPrice=num(form.fixedPrice)
  if(form.pricingMode==='VOUCHER')payload.voucherId=form.voucherId||null
  return payload
}
function mapPreviewLine(l,current){return {...makeLine({...l,qty:num(l.qty)}),tempId:current?.tempId||'t'+(seq++),productLabel:`${l.productCode||''} ${l.productName||''}`.trim(),lineLevel:l.lineLevel||'NORMAL',isGift:(l.isGift===true||l.gift===true),isBom:l.lineLevel==='PARENT',bomVersion:l.bomVersion||current?.bomVersion||null,bomGroupNo:l.bomGroupNo||current?.bomGroupNo||null,componentQty:num(l.componentQty),lineDiscountType:current?.lineDiscountType||l.lineDiscountType||'',lineDiscountDirection:current?.lineDiscountDirection||l.lineDiscountDirection||'REDUCE',lineDiscountValue:num(current?.lineDiscountValue??l.lineDiscountValue),lineZero:!!(current?.lineZero||l.lineZero),children:[],batchNo:l.batchNo||current?.batchNo||'',serialNo:l.serialNo||current?.serialNo||'',stockId:l.consignmentStockId??l.stockId??current?.stockId??null}}
function applyPreview(data,fullRefresh){
  const existingGifts=fullRefresh?[]:flatLines.value.filter(l=>l.isGift).map(l=>({...l}));
  const roots=editableRoots.value;let rootIndex=0;let currentParent=null;const next=[];
  (data.lines||[]).forEach(l=>{
    if(l.isGift||l.gift){next.push(mapPreviewLine(l));return}
    const current=l.lineLevel==='CHILD'?null:roots[rootIndex++];
    const row=mapPreviewLine(l,current);
    if(l.lineLevel==='PARENT'){currentParent=row;row._origChildren=current?[...(current.children||[])]:[];next.push(row)}
    else if(l.lineLevel==='CHILD'&&currentParent&&l.bomGroupNo===currentParent.bomGroupNo){
      const curChild=(currentParent._origChildren||[]).find(c=>String(c.productId)===String(l.productId));
      if(curChild){row.lineDiscountType=curChild.lineDiscountType||'';row.lineDiscountDirection=curChild.lineDiscountDirection||'REDUCE';row.lineDiscountValue=num(curChild.lineDiscountValue)}
      currentParent.children.push(row)
    }
    else{currentParent=null;next.push(row)}
  });
  next.forEach(r=>{if(r.lineLevel==='PARENT'){delete r._origChildren}});
  next.push(...existingGifts);
  form.lines=next;
  form.amountInclTax=num(data.amountInclTax);form.discountAmount=num(data.discountAmount);form.finalAmount=num(data.finalAmount);
  form.taxAmount=num(data.taxAmount);form.amountExclTax=num(data.amountExclTax);
  Object.assign(summary,{productDiscountTotal:num(data.productDiscountTotal),promoDiscountTotal:num(data.promoDiscountTotal),lineDiscountTotal:num(data.lineDiscountTotal),dealerDiscountTotal:num(data.dealerDiscountTotal),headerDiscountTotal:num(data.headerDiscountTotal),voucherAmount:num(data.voucherAmount),payableAmount:num(data.payableAmount)});
  if(fullRefresh)promoMessages.value=Array.isArray(data.promotionMessages)?data.promotionMessages:[];
  if(data.pricingMode&&data.pricingMode!==form.pricingMode&&!form.pricingMode)form.pricingMode=data.pricingMode;
  if(fullRefresh&&form.pricingMode==='NORMAL'&&form.dealerId)loadVouchers()
}
async function executePreview(applyPromotions){
  if(!form.dealerId){form.finalAmount=0;Object.assign(summary,{productDiscountTotal:0,promoDiscountTotal:0,lineDiscountTotal:0,dealerDiscountTotal:0,headerDiscountTotal:0,voucherAmount:0,payableAmount:0});return null}
  if(!editableRoots.value.some(l=>l.productId))return null;
  const token=++previewToken;
  if(applyPromotions)refreshing.value=true;else previewLoading.value=true;
  try{
    const {data}=await calcPreview(buildPreviewPayload(applyPromotions));
    if(token===previewToken){error.value='';applyPreview(data,applyPromotions);return data}
    return null
  }catch(e){if(token===previewToken){const msg=e.response?.data?.message||e.message||'';error.value=msg;ElMessage.error(msg||'计价失败')}return null}
  finally{if(token===previewToken){refreshing.value=false;previewLoading.value=false}}
}
function runPreview(){return executePreview(false)}
function refreshPromotions(){return executePreview(true)}
function schedulePreview(){clearTimeout(previewTimer);previewTimer=setTimeout(runPreview,300)}
async function onSave(submit){
  if(saving.value||submitting.value){ElMessage.warning('请求正在处理中，请勿重复提交');return}
  const ok=await formRef.value.validate().catch(()=>false);if(!ok)return
  if(!editableRoots.value.length)return ElMessage.error('请至少添加一行')
  if(editableRoots.value.some(l=>!l.productId||!Number.isInteger(num(l.qty))||num(l.qty)<=0))return ElMessage.error('请完善产品和正整数数量')
  const dup=new Set();const dupSku=form.orderType==='INVOICE'?null:editableRoots.value.find(l=>{if(!l.productId)return false;if(dup.has(String(l.productId)))return true;dup.add(String(l.productId));return false});if(dupSku)return ElMessage.error(`产品「${dupSku.productCode||dupSku.productName}」存在重复行，请合并为一行后提交`)
  await refreshPromotions().catch(()=>null)
  if(error.value)return ElMessage.error('当前计价存在问题，请先按提示修正后再提交：'+error.value)
  const chargeable=flatLines.value.filter(l=>!l.isGift&&l.lineLevel!=='PARENT')
  const missing=chargeable.filter(l=>!num(l.basePriceInclTax??l.standardPriceInclTax))
  if(missing.length){const names=missing.map(l=>l.productCode||l.productName||`#${l.productId}`).join('、');return ElMessage.error(`以下产品没有有效销售价格，请先在产品价格中维护：${names}`)}
  saving.value=!submit;submitting.value=submit;error.value=''
  try{
    const payload=buildPreviewPayload(true);payload.remark=form.remark;payload.shipAddressId=form.shipAddressId||null
    payload.extra={shipAddressId:form.shipAddressId||null,pricingMode:form.pricingMode,voucherId:form.voucherId||null,fixedPrice:num(form.fixedPrice)||null};payload.terminalHospitalId=form.terminalHospitalId||null;payload.sampleReason=form.sampleReason||null
    const res=isEdit.value?await request({url:`/api/sales-orders/${form.id}`,method:'put',data:payload}):await request({url:'/api/sales-orders',method:'post',data:payload})
    if(submit)await request({url:`/api/sales-orders/${form.id||res.data.id}/submit`,method:'post'})
    ElMessage.success(submit?'销售订单已提交':'销售订单已保存');router.push('/m/orders')
  }catch(e){const msg=e.response?.data?.message||e.message||'保存失败';error.value=msg;ElMessage.error(msg)}
  finally{saving.value=false;submitting.value=false}
}
async function loadOrder(){
  const d=(await request({url:`/api/sales-orders/${route.params.id}`})).data
  const savedExtra=d.extra&&typeof d.extra==='object'?d.extra:{}
  Object.assign(form,{id:d.id,code:d.code,status:d.status||'DRAFT',dealerId:d.dealerId,dealerName:d.dealerName||'',orderType:d.orderType||'SALES',expectedDate:d.expectedDate||'',remark:d.remark||'',pricingMode:savedExtra.pricingMode||d.pricingMode||'NORMAL',fixedPrice:savedExtra.fixedPrice??null,voucherId:savedExtra.voucherId||d.voucherId||null,shipAddressId:savedExtra.shipAddressId||d.shipAddressId||null,headerDiscountType:d.headerDiscountType||'',headerDiscountDirection:'REDUCE',headerDiscountValue:num(d.headerDiscountValue),amountInclTax:num(d.amountInclTax),discountAmount:num(d.discountAmount),finalAmount:num(d.finalAmount),taxAmount:num(d.taxAmount),amountExclTax:num(d.amountExclTax)})
  if(Array.isArray(d.lines)){form.lines=d.lines.filter(l=>!l.bomParentLineId).map(l=>({...makeLine(l),productLabel:`${l.productCode||''} ${l.productName||''}`,qty:num(l.qty),standardPriceInclTax:num(l.standardPriceInclTax??l.unitPrice),basePriceInclTax:num(l.basePriceInclTax??l.standardPriceInclTax??l.unitPrice),lineLevel:l.lineLevel||(l.isGroupHeader?'PARENT':'NORMAL'),children:d.lines.filter(c=>String(c.bomParentLineId)===String(l.id)).map(c=>({...makeLine(c),productLabel:`${c.productCode||''} ${c.productName||''}`,qty:num(c.qty),standardPriceInclTax:num(c.standardPriceInclTax??c.unitPrice),basePriceInclTax:num(c.basePriceInclTax??c.standardPriceInclTax??c.unitPrice),lineLevel:'CHILD'}))}))}
  prevDealerId=form.dealerId
  await loadAddresses()
  if(form.pricingMode==='VOUCHER')await loadVouchers()
  schedulePreview()
}
function resetForm(){Object.assign(form,{id:null,code:'',status:'DRAFT',dealerId:null,dealerName:'',orderType:'SALES',expectedDate:todayStr(),shipAddressId:null,remark:'',pricingMode:'NORMAL',fixedPrice:null,voucherId:null,headerDiscountType:'',headerDiscountDirection:'REDUCE',headerDiscountValue:0,lines:[],amountInclTax:0,discountAmount:0,finalAmount:0,taxAmount:0,amountExclTax:0});Object.assign(summary,{productDiscountTotal:0,promoDiscountTotal:0,lineDiscountTotal:0,dealerDiscountTotal:0,headerDiscountTotal:0,voucherAmount:0,payableAmount:0});promoMessages.value=[];addresses.value=[];vouchers.value=[];saving.value=false;submitting.value=false;error.value='';prevDealerId=null;consignDialog.selected=[];consignDialog.rows=[];consignDialog.warehouses=[];consignDialog.keyword='';consignDialog.warehouseId=null;nextTick(()=>{formRef.value?.clearValidate?.();formRef.value?.resetFields?.()})}
function handleRouteChange(){if(isEdit.value){loadOrder()}else{resetForm()}}
onMounted(handleRouteChange)
onActivated(handleRouteChange)
watch(()=>route.params.id,handleRouteChange)
</script>
<style scoped>
.order-create-page .area-scroll{padding:0}.form-container{padding:16px}.page-header{display:flex;align-items:center;justify-content:space-between;padding:14px 20px;background:#fff;border-bottom:1px solid #ebeef5}.page-title{display:flex;align-items:center;gap:10px}.page-title h3{margin:0;font-size:18px}.page-alert{margin:12px 16px 0}.promo-alert{white-space:pre-line}.form-container{padding:16px;display:flex;flex-direction:column;gap:12px}.lines-header{display:flex;justify-content:space-between;align-items:center}.lines-header>div{display:flex;gap:8px;align-items:center}.product-cell{display:flex;gap:6px;align-items:center}.child-product-text{display:flex;gap:8px;align-items:center;min-width:0}.child-product-code{color:#606266;font-weight:600;flex:0 0 auto}.child-product-name{color:#303133;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.price-cell{display:flex;flex-direction:column;align-items:flex-end;gap:2px}.discount-input{display:flex;gap:8px;width:100%;max-width:560px;align-items:center}.discount-input .el-select{width:130px;flex:0 0 130px}.discount-input .el-input-number{flex:1 1 auto;width:auto}.line-discount{display:flex;gap:4px;align-items:center}.muted{color:#909399}.small{font-size:12px}.mode-block{margin-bottom:18px}.block-title{font-weight:600;color:#303133;margin-bottom:10px}.mode-input{display:flex;align-items:center;gap:10px;margin-top:12px;flex-wrap:wrap}.mode-tip{margin-top:10px}.settle-block{background:#fafafa;border:1px solid #ebeef5;border-radius:2px;padding:14px 16px}.settle-row{display:flex;justify-content:space-between;align-items:center;font-size:13px;color:#606266;padding:4px 0}.settle-row.total{font-size:15px;color:#303133;font-weight:600}.settle-row.payable{font-size:17px;color:var(--el-color-danger);font-weight:700}.settle-row.voucher{color:var(--el-color-warning)} .settle-row.signed{font-variant-numeric:tabular-nums}
.consign-row-picked>td.el-table__cell{background-color:var(--el-color-warning-light-9)!important}
</style>
