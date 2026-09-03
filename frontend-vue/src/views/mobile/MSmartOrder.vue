<template>
  <div class="smart-order">
    <van-nav-bar title="智能下单" left-arrow @click-left="$router.back()">
      <template #right>
        <span class="restart-link" @click="restart">↺ 重新开始</span>
      </template>
    </van-nav-bar>

    <div ref="scrollRef" class="chat-scroll">
      <div
        v-for="(m, i) in messages"
        :key="i"
        class="msg-row"
        :class="m.role === 'user' ? 'is-user' : 'is-bot'"
      >
        <div v-if="m.role === 'bot'" class="avatar bot-avatar">DMS</div>
        <div class="bubble" :class="m.role">
          <div v-if="m.title" class="b-title">{{ m.title }}</div>
          <div v-if="m.text" class="b-text" v-html="m.text"></div>

          <!-- 选项按钮 -->
          <div v-if="m.options && m.options.length" class="opt-list">
            <van-button
              v-for="opt in m.options"
              :key="opt.key"
              class="opt-btn"
              :type="opt.danger ? 'danger' : 'primary'"
              :plain="!opt.primary"
              block
              :disabled="!!m.disabled"
              @click="onOption(m, opt)"
            >
              {{ opt.label }}
            </van-button>
          </div>

          <!-- 摘要卡片 -->
          <div v-if="m.summary" class="summary-card">
            <div class="sum-row"><span>订单类型</span><span>{{ typeLabel(m.summary.orderType) }}</span></div>
            <div class="sum-row"><span>客户</span><span class="sum-r">{{ m.summary.dealerName }}</span></div>
            <div class="sum-lines">
              <div v-for="(l, li) in m.summary.lines" :key="li" class="sum-line">
                <div class="sl-head">
                  <span class="sl-no">#{{ li + 1 }}</span>
                  <span class="sl-name">{{ l.productCode }} · {{ l.productName }}</span>
                </div>
                <div class="sl-meta">
                  <span>x{{ l.qty }}</span>
                  <span v-if="l.lineDiscountType">行折扣：{{ l.lineDiscountType === 'PERCENT' ? (Number(l.lineDiscountValue)/10).toFixed(1).replace('.0','') + ' 折' : '减 ¥' + Number(l.lineDiscountValue || 0).toFixed(2) }}</span>
                </div>
              </div>
            </div>
            <div v-if="m.summary.headerText" class="sum-row"><span>整单折扣</span><span class="sum-r">{{ m.summary.headerText }}</span></div>
            <div v-if="m.summary.voucherText" class="sum-row"><span>代金券</span><span class="sum-r">{{ m.summary.voucherText }}</span></div>
            <div v-if="m.summary.fixedPriceText" class="sum-row"><span>一口价</span><span class="sum-r">{{ m.summary.fixedPriceText }}</span></div>
            <div v-if="m.summary.promoMessages && m.summary.promoMessages.length" class="promo-lines">
              <div v-for="(p, pi) in m.summary.promoMessages" :key="pi" class="promo-line">🎁 {{ p }}</div>
            </div>
            <div class="sum-total">
              <span>{{ m.summary.zeroOrder ? '本单金额' : '应付金额' }}</span>
              <span class="sum-amt">¥{{ Number(m.summary.payable || 0).toFixed(2) }}</span>
            </div>
          </div>
        </div>
        <div v-if="m.role === 'user'" class="avatar user-avatar">我</div>
      </div>

      <div v-if="loading" class="msg-row is-bot">
        <div class="avatar bot-avatar">DMS</div>
        <div class="bubble bot"><van-loading size="16" /> 处理中…</div>
      </div>
    </div>

    <!-- 数量快捷条：数量步可点选/步进 -->
    <div v-if="inputMode && inputKind === 'qty'" class="qty-bar">
      <div class="qty-stepper">
        <van-button size="small" plain icon="minus" @click="qtyStep(-1)" />
        <span class="qty-val">{{ qtyDraft || 1 }}</span>
        <van-button size="small" plain icon="plus" @click="qtyStep(1)" />
      </div>
      <div class="qty-quick">
        <van-button v-for="n in [1,5,10,20,50,100]" :key="n" size="mini" plain round @click="qtySet(n)">{{ n }}</van-button>
      </div>
      <van-button size="small" type="primary" block round @click="qtyConfirm()">确定 {{ qtyDraft || 1 }} 件</van-button>
    </div>
    <!-- 输入栏：仅自由输入步骤显示（数量步保留输入框也可直接打字） -->
    <div v-if="inputMode" class="input-bar">
      <van-field
        v-model="inputValue"
        :type="inputType"
        :placeholder="inputPlaceholder"
        clearable
        @keyup.enter="onSend"
      >
        <template #button>
          <van-button size="small" type="primary" :loading="loading" @click="onSend">发送</van-button>
        </template>
      </van-field>
      <div class="input-hint">输入 0 可随时重新开始</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { showFailToast } from 'vant'
import { lookup } from '@/api/crud'
import { previewV43, createSalesOrderV43, submitSalesOrderV43, availableVouchers, listDealerAddresses } from '@/api/mobileV43'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()

const num = v => Number(v || 0)

const ORDER_TYPES = [
  { value: 'SALES', label: '1. 销售订单', desc: '正常销售出库，可设置折扣/代金券' },
  { value: 'REPLENISHMENT', label: '2. 补货订单', desc: '寄售补货，0 金额出库' },
  { value: 'SAMPLE', label: '3. 样品订单', desc: '样品申领，0 金额，需填申请原因' }
]

const messages = ref([])
const loading = ref(false)
const scrollRef = ref(null)

const step = ref('')
const inputMode = ref(false)
const inputType = ref('text')
const inputPlaceholder = ref('')
const inputValue = ref('')
const inputKind = ref('')
const qtyDraft = ref(1)

const state = reactive({
  orderType: '',
  dealerId: null,
  dealerName: '',
  consignmentMap: {},
  lines: [],
  sampleReason: '',
  lineDiscountType: '',
  lineDiscountValue: 0,
  headerDiscountType: '',
  headerDiscountValue: 0,
  pricingMode: 'NORMAL',
  fixedPrice: null,
  voucherId: null,
  voucherLabel: '',
  address: null
})

const dealers = ref([])
let productResults = []
let voucherResults = []
const PAGE_SIZE = 5
const listPage = ref(0)        // 当前产品/客户列表批次（0 起）
let listKind = ''              // 'product' | 'dealer' | 'voucher'
function listMaxPage(len) { return Math.max(0, Math.ceil(len / PAGE_SIZE) - 1) }
// 构造某一类列表某一批的选项（5 个/批 + 上一批/下一批）
function buildPagedOptions(kind, all) {
  const total = all.length
  const max = listMaxPage(total)
  const pg = Math.min(listPage.value, max)
  const start = pg * PAGE_SIZE
  const slice = all.slice(start, start + PAGE_SIZE)
  let opts
  if (kind === 'product') {
    opts = slice.map((p, i) => ({
      key: 'p' + p.id,
      label: (start + i + 1) + '. ' + (p.code || '') + ' · ' + (p.nameCn || p.name || '') + (p.spec ? ' / ' + p.spec : ''),
      action: 'product', value: start + i
    }))
  } else if (kind === 'dealer') {
    opts = slice.map((d, i) => ({
      key: 'd' + d.id,
      label: (start + i + 1) + '. ' + d.label,
      action: 'dealer', value: d.id
    }))
  } else {
    opts = slice.map((v, i) => ({
      key: 'v' + v.id,
      label: (start + i + 1) + '. ¥' + num(v.faceValue).toFixed(2) + ' ' + (v.name || '代金券') + (num(v.minSpend) > 0 ? '（满 ¥' + num(v.minSpend).toFixed(2) + '）' : '（无门槛）'),
      action: 'voucher', value: start + i
    }))
  }
  const nav = []
  if (pg > 0) nav.push({ key: 'prev-page', label: '‹ 上一批', action: 'page', value: 'prev' })
  if (pg < max) nav.push({ key: 'next-page', label: '下一批 ›（还有 ' + (total - start - slice.length) + ' 个）', action: 'page', value: 'next' })
  return { opts: [...opts, ...nav], pageInfo: '第 ' + (pg + 1) + '/' + (max + 1) + ' 批 · 共 ' + total + ' 个' }
}
function rerenderPaged() {
  // 翻页：替换最后一条分页消息为同一批数据的新批次选项（整对象替换确保 Vue 重渲染）；
  // 非数据按钮（如"进入下一步"/"不使用券"）以 _pin 保留在最前。
  const msgs = messages.value
  for (let i = msgs.length - 1; i >= 0; i--) {
    if (msgs[i].role === 'bot' && msgs[i].options && msgs[i]._paged) {
      const all = listKind === 'product' ? productResults : listKind === 'dealer' ? dealers.value : voucherResults
      const { opts: dataOpts, pageInfo } = buildPagedOptions(listKind, all)
      const pinned = (msgs[i].options || []).filter(o => o._pin)
      // 标题里的“第 x/y 批”用最新 pageInfo 刷新
      const baseTpl = msgs[i]._baseTitle || msgs[i].title || ''
      const newTitle = baseTpl.includes('{PAGE}') ? baseTpl.replace('{PAGE}', pageInfo) : baseTpl
      msgs[i] = {
        ...msgs[i],
        title: newTitle,
        options: [...pinned, ...dataOpts],
        _paged: true,
        _baseTitle: baseTpl
      }
      scrollBottom()
      return
    }
  }
}


function typeLabel(t) {
  return { SALES: '销售订单', REPLENISHMENT: '补货订单', SAMPLE: '样品订单' }[t] || t || '-'
}
function escapeHtml(s) {
  return String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
}

function scrollBottom() {
  nextTick(() => {
    const el = scrollRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function bot(content) {
  messages.value.push({ role: 'bot', ...content })
  scrollBottom()
}
function userSay(text) {
  messages.value.push({ role: 'user', text: String(text) })
  scrollBottom()
}

function setInput(kind, placeholder, type = 'text') {
  inputMode.value = true
  inputKind.value = kind
  inputType.value = type
  inputPlaceholder.value = placeholder
  inputValue.value = ''
  if (kind === 'qty') qtyDraft.value = 1
}
function hideInput() {
  inputMode.value = false
  inputKind.value = ''
  inputValue.value = ''
}

function restart() {
  Object.assign(state, {
    orderType: '', dealerId: null, dealerName: '', lines: [], sampleReason: '',
    lineDiscountType: '', lineDiscountValue: 0, headerDiscountType: '', headerDiscountValue: 0,
    pricingMode: 'NORMAL', fixedPrice: null, voucherId: null, voucherLabel: '', address: null
  })
  hideInput()
  messages.value = []
  start()
}

function start() {
  step.value = 'type'
  hideInput()
  bot({
    title: '欢迎使用智能下单 👋',
    text: '我会一步步引导您完成下单，全程点选即可，只有搜索产品和填写数量时需要打字。<br/>随时可点右上角「↺ 重新开始」。<br/><br/><b>第一步：请选择订单类型</b>',
    options: ORDER_TYPES.map(t => ({ key: t.value, label: t.label + '（' + t.desc + '）', action: 'type', value: t.value }))
  })
}

async function loadDealers() {
  loading.value = true
  try {
    const [dRes, cRes] = await Promise.all([
      lookup('dealers', { limit: 200 }),
      request({ url: '/api/dealer-credit', method: 'get', params: { page: 1, size: 200 } }).catch(() => null)
    ])
    dealers.value = (dRes.data || []).map(d => ({
      id: d.id, code: d.code, name: d.name,
      label: (d.code ? d.code + ' · ' : '') + d.name,
      status: d.status
    })).filter(d => !d.status || String(d.status).toUpperCase() === 'ACTIVE')
    const creditList = cRes?.data?.list || cRes?.data?.records || cRes?.data || []
    const arr = Array.isArray(creditList) ? creditList : []
    state.consignmentMap = {}
    arr.forEach(c => { state.consignmentMap[c.dealerId] = !!c.consignmentEnabled })
  } catch (e) {
    bot({ text: '客户列表加载失败：' + escapeHtml(e?.response?.data?.message || e.message) + '，请点右上角重新开始重试。' })
  } finally {
    loading.value = false
  }
}

function askDealer() {
  step.value = 'dealer'
  hideInput()
  const u = userStore.user || {}
  const ut = String(u.userType || u.user_type || '').toLowerCase()
  const lockedDealerId = (ut === 'dealer' || ut === 'customer') ? (u.dealerId || u.dealer_id) : null
  if (lockedDealerId) {
    const locked = dealers.value.find(d => String(d.id) === String(lockedDealerId))
    state.dealerId = lockedDealerId
    state.dealerName = locked ? locked.label : (u.dealerName || u.companyName || ('#' + lockedDealerId))
    bot({ text: '已识别您的经销商账户，客户已锁定：<b>' + escapeHtml(state.dealerName) + '</b>' })
    afterDealer()
    return
  }
  if (!dealers.value.length) {
    bot({ text: '当前没有可选客户（经销商），请联系管理员开通授权。', options: [{ key: 'restart', label: '↺ 重新开始', action: 'restart' }] })
    return
  }
  listKind = 'dealer'; listPage.value = 0
  const dPaged = buildPagedOptions('dealer', dealers.value)
  bot({ title: '第二步：请选择下单客户（' + dPaged.pageInfo + '）', text: '直接点击下方客户；客户较多时用「下一批」翻页：', options: dPaged.opts, _paged: true, _baseTitle: '第二步：请选择下单客户（{PAGE}）' })
}

function afterDealer() {
  if (state.orderType === 'REPLENISHMENT' && !state.consignmentMap[state.dealerId]) {
    bot({
      text: '⚠️ 客户 <b>' + escapeHtml(state.dealerName) + '</b> 尚未开通寄售库存，无法下补货订单。',
      options: [
        { key: 're-dealer', label: '重新选择客户', action: 'askDealer' },
        { key: 'restart', label: '↺ 重新开始（更换订单类型）', action: 'restart' }
      ]
    })
    return
  }
  askProductSearch()
}

function askProductSearch() {
  step.value = 'product-search'
  setInput('product-search', '输入产品编码 / 名称 / 型号关键词，如：吻合器')
  bot({ title: '第三步：要下什么产品？', text: '请输入<b>产品描述、型号或产品编码</b>关键词，我来帮您搜索。' })
}

async function doProductSearch(keyword) {
  loading.value = true
  try {
    const r = await lookup('products', { keyword, dealerId: state.dealerId, limit: 100 })
    const d = r.data
    productResults = Array.isArray(d) ? d : (d.list || d.records || [])
    if (!productResults.length) {
      bot({
        text: '没有搜到与「<b>' + escapeHtml(keyword) + '</b>」匹配的产品，请换个关键词再试。',
        options: [{ key: 'research', label: '🔍 换个关键词重搜', action: 'product-search' }]
      })
      setInput('product-search', '重新输入产品关键词')
      return
    }
    listKind = 'product'; listPage.value = 0
    const pPaged = buildPagedOptions('product', productResults)
    if (state.lines.length) pPaged.opts.unshift({ key: 'cancel-add', label: '✔ 不新增了，进入下一步 ›', action: 'after-lines', primary: true, _pin: true })
    hideInput()
    bot({ title: '找到 ' + productResults.length + ' 个产品（' + pPaged.pageInfo + '），请点击选择：', text: '每批显示 ' + PAGE_SIZE + ' 个，点「下一批」查看更多：', options: pPaged.opts, _paged: true, _baseTitle: '找到 ' + productResults.length + ' 个产品（{PAGE}），请点击选择：' })
  } catch (e) {
    bot({ text: '产品搜索失败：' + escapeHtml(e?.response?.data?.message || e.message) })
    setInput('product-search', '重新输入产品关键词')
  } finally {
    loading.value = false
  }
}

function onProductPick(idx) {
  const p = productResults[idx]
  if (!p) return
  if (state.lines.some(l => String(l.productId) === String(p.id))) {
    showFailToast('该产品已在订单中，不可重复添加')
    return
  }
  state.lines.push({
    productId: p.id,
    productCode: p.code || '',
    productName: p.nameCn || p.name || '',
    productSpec: p.spec || '',
    qty: 1,
    lineDiscountType: '',
    lineDiscountValue: 0
  })
  hideInput()
  bot({ text: '已添加：<b>' + escapeHtml((p.code || '') + ' · ' + (p.nameCn || p.name || '')) + '</b>' })
  askQty()
}

function askQty() {
  step.value = 'qty'
  setInput('qty', '输入数量（正整数）', 'tel')
  bot({ title: '第四步：下单数量？', text: '请输入该产品的<b>正整数</b>数量，例如 10。' })
}

function qtyStep(delta) {
  qtyDraft.value = Math.max(1, (Number(qtyDraft.value) || 1) + delta)
  inputValue.value = String(qtyDraft.value)
}
function qtySet(n) {
  qtyDraft.value = n
  inputValue.value = String(n)
}
function qtyConfirm() {
  if (!qtyDraft.value || Number(qtyDraft.value) <= 0) { showFailToast('数量必须大于 0'); return }
  onQtyInput(String(qtyDraft.value))
}
function onQtyInput(text) {
  const qty = Number(text)
  if (!/^\d+$/.test(text.trim()) || qty <= 0) {
    bot({ text: '⚠️ 数量必须是大于 0 的整数，请重新输入。' })
    setInput('qty', '重新输入数量（正整数）', 'tel')
    return
  }
  state.lines[state.lines.length - 1].qty = qty
  userSay(qty)
  if (state.orderType === 'SAMPLE') askSampleReason()
  else if (state.orderType === 'SALES') askLineDiscount()
  else afterLineAdded()
}

function askSampleReason() {
  step.value = 'sample-reason'
  setInput('sample-reason', '填写样品申请原因（必填，如：XX医院骨科试用）')
  bot({ title: '样品申请原因', text: '样品订单必须填写<b>申请原因</b>（医院/科室/用途），请输入：' })
}

function onSampleReason(text) {
  state.sampleReason = text.trim()
  userSay(text)
  afterLineAdded()
}

function askLineDiscount() {
  step.value = 'line-discount'
  hideInput()
  bot({
    title: '第五步：这个产品需要行折扣吗？',
    options: [
      { key: 'none', label: '1. 无折扣', action: 'line-discount', value: '' },
      { key: 'percent', label: '2. 按百分比折扣（9 折填 90）', action: 'line-discount', value: 'PERCENT' },
      { key: 'amount', label: '3. 按固定金额减（每行减 ¥）', action: 'line-discount', value: 'AMOUNT' }
    ]
  })
}

function onLineDiscountType(type) {
  const line = state.lines[state.lines.length - 1]
  if (!type) {
    line.lineDiscountType = ''
    line.lineDiscountValue = 0
    afterLineAdded()
    return
  }
  state.lineDiscountType = type
  if (type === 'PERCENT') {
    step.value = 'line-discount-percent'
    setInput('line-discount-percent', '折扣百分比 0~100，如 90 表示 9 折', 'tel')
    bot({ text: '请输入<b>折扣百分比</b>（0~100，不含 0 和 100）：' })
  } else {
    step.value = 'line-discount-amount'
    setInput('line-discount-amount', '每行减免金额（元，大于 0）', 'digit')
    bot({ text: '请输入每行<b>固定减免金额</b>（元，大于 0）：' })
  }
}

function onLineDiscountValue(text) {
  const line = state.lines[state.lines.length - 1]
  const v = Number(text)
  if (!/^\d+(\.\d+)?$/.test(text.trim()) || v <= 0 || (state.lineDiscountType === 'PERCENT' && v >= 100)) {
    bot({ text: state.lineDiscountType === 'PERCENT' ? '⚠️ 百分比需在 0~100 之间（如 90 表示 9 折），请重新输入。' : '⚠️ 减免金额必须大于 0，请重新输入。' })
    setInput(state.lineDiscountType === 'PERCENT' ? 'line-discount-percent' : 'line-discount-amount', '请重新输入', state.lineDiscountType === 'PERCENT' ? 'tel' : 'digit')
    return
  }
  line.lineDiscountType = state.lineDiscountType
  line.lineDiscountValue = v
  userSay(text)
  afterLineAdded()
}

function afterLineAdded() {
  if (state.orderType === 'SAMPLE') { afterAllLines(); return }
  step.value = 'add-more'
  hideInput()
  bot({
    title: '第六步：还要继续新增产品吗？',
    options: [
      { key: 'yes', label: '1. 是，继续添加产品', action: 'add-more', value: 'yes' },
      { key: 'no', label: '2. 否，产品已添加完毕', action: 'add-more', value: 'no' }
    ]
  })
}

function afterAllLines() {
  if (state.orderType === 'SALES') askHeaderDiscount()
  else askConfirm()
}

function askHeaderDiscount() {
  step.value = 'header-discount'
  hideInput()
  state.pricingMode = 'NORMAL'
  bot({
    title: '第七步：需要整单优惠吗？',
    options: [
      { key: 'none', label: '1. 无整单优惠', action: 'header', value: 'NONE' },
      { key: 'header', label: '2. 整单折扣（金额 / 百分比）', action: 'header', value: 'HEADER' },
      { key: 'fixed', label: '3. 整单一口价', action: 'header', value: 'FIXED' },
      { key: 'voucher', label: '4. 使用代金券', action: 'header', value: 'VOUCHER' }
    ]
  })
}

function onHeaderChoice(choice) {
  if (choice === 'NONE') {
    state.pricingMode = 'NORMAL'
    state.headerDiscountType = ''
    state.headerDiscountValue = 0
    state.voucherId = null
    askConfirm()
  } else if (choice === 'HEADER') {
    step.value = 'header-type'
    hideInput()
    bot({
      title: '第八步：整单折扣方式',
      options: [
        { key: 'percent', label: '1. 按百分比（95 折填 95）', action: 'header-type', value: 'PERCENT' },
        { key: 'amount', label: '2. 按固定金额减（整单减 ¥）', action: 'header-type', value: 'AMOUNT' }
      ]
    })
  } else if (choice === 'FIXED') {
    state.pricingMode = 'FIXED_PRICE'
    state.headerDiscountType = ''
    state.voucherId = null
    step.value = 'fixed-price'
    setInput('fixed-price', '输入整单一口价金额（元，大于 0）', 'digit')
    bot({ text: '请输入<b>整单一口价</b>金额（元，大于 0）：' })
  } else if (choice === 'VOUCHER') {
    askVoucher()
  }
}

function onHeaderType(type) {
  state.headerDiscountType = type
  if (type === 'PERCENT') {
    step.value = 'header-percent'
    setInput('header-percent', '整单折扣百分比 0~100，如 95', 'tel')
    bot({ text: '请输入整单<b>折扣百分比</b>（0~100，不含 0 和 100）：' })
  } else {
    step.value = 'header-amount'
    setInput('header-amount', '整单减免金额（元，大于 0）', 'digit')
    bot({ text: '请输入整单<b>固定减免金额</b>（元，大于 0）：' })
  }
}

function onHeaderValue(text) {
  const v = Number(text)
  const isPercent = state.headerDiscountType === 'PERCENT'
  if (!/^\d+(\.\d+)?$/.test(text.trim()) || v <= 0 || (isPercent && v >= 100)) {
    bot({ text: isPercent ? '⚠️ 百分比需在 0~100 之间，请重新输入。' : '⚠️ 减免金额必须大于 0，请重新输入。' })
    setInput(isPercent ? 'header-percent' : 'header-amount', '请重新输入', isPercent ? 'tel' : 'digit')
    return
  }
  state.pricingMode = 'NORMAL'
  state.headerDiscountValue = v
  userSay(text)
  askConfirm()
}

function onFixedPrice(text) {
  const v = Number(text)
  if (!/^\d+(\.\d+)?$/.test(text.trim()) || v <= 0) {
    bot({ text: '⚠️ 一口价金额必须大于 0，请重新输入。' })
    setInput('fixed-price', '重新输入一口价金额（元）', 'digit')
    return
  }
  state.fixedPrice = v
  userSay('¥' + text)
  askConfirm()
}

async function askVoucher() {
  step.value = 'voucher'
  hideInput()
  state.pricingMode = 'VOUCHER'
  loading.value = true
  bot({ text: '正在查询可用代金券…' })
  try {
    const productIds = state.lines.map(l => l.productId).filter(Boolean).join(',')
    const r = await availableVouchers({ dealerId: state.dealerId, productIds: productIds || undefined })
    voucherResults = Array.isArray(r?.data) ? r.data : []
    if (!voucherResults.length) {
      state.pricingMode = 'NORMAL'
      state.voucherId = null
      bot({
        text: '当前客户/产品<b>没有可用代金券</b>。请选择其他整单优惠方式：',
        options: [
          { key: 'back', label: '返回整单优惠选择', action: 'ask-header' },
          { key: 'none', label: '不使用整单优惠，直接下一步', action: 'header', value: 'NONE' }
        ]
      })
      return
    }
    listKind = 'voucher'; listPage.value = 0
    const vPaged = buildPagedOptions('voucher', voucherResults)
    vPaged.opts.unshift({ key: 'cancel-voucher', label: '✖ 不使用代金券，返回重选优惠方式', action: 'ask-header', _pin: true })
    bot({ title: '请选择代金券（' + vPaged.pageInfo + '）：', options: vPaged.opts, _paged: true, _baseTitle: '请选择代金券（{PAGE}）：' })
  } catch (e) {
    state.pricingMode = 'NORMAL'
    bot({
      text: '代金券查询失败：' + escapeHtml(e?.response?.data?.message || e.message),
      options: [{ key: 'back', label: '返回整单优惠选择', action: 'ask-header' }]
    })
  } finally {
    loading.value = false
  }
}

function onVoucherPick(idx) {
  const v = voucherResults[idx]
  if (!v) return
  state.voucherId = v.id
  state.voucherLabel = '¥' + num(v.faceValue).toFixed(2) + ' ' + (v.name || '代金券')
  bot({ text: '已选代金券：<b>' + escapeHtml(state.voucherLabel) + '</b>' })
  askConfirm()
}

function buildPayload() {
  const isZero = state.orderType !== 'SALES'
  return {
    orderType: state.orderType || 'SALES',
    dealerId: state.dealerId,
    sampleReason: state.orderType === 'SAMPLE' ? state.sampleReason : null,
    applyPromotions: state.orderType === 'SALES',
    pricingMode: isZero ? 'NORMAL' : (state.pricingMode || 'NORMAL'),
    fixedPrice: !isZero && state.pricingMode === 'FIXED_PRICE' ? num(state.fixedPrice) : null,
    voucherId: !isZero && state.pricingMode === 'VOUCHER' ? state.voucherId : null,
    headerDiscountType: !isZero && state.pricingMode === 'NORMAL' && state.headerDiscountType ? state.headerDiscountType : null,
    headerDiscountValue: !isZero && state.pricingMode === 'NORMAL' && state.headerDiscountType ? num(state.headerDiscountValue) : null,
    headerDiscountDirection: 'REDUCE',
    lines: state.lines.map(l => ({
      productId: l.productId,
      qty: Math.max(1, Math.floor(num(l.qty)) || 1),
      lineZero: isZero,
      lineDiscountType: !isZero && l.lineDiscountType ? l.lineDiscountType : null,
      lineDiscountValue: !isZero && l.lineDiscountType ? num(l.lineDiscountValue) : null,
      lineDiscountDirection: 'REDUCE'
    }))
  }
}

async function runPreview() {
  const { data } = await previewV43(buildPayload())
  return data || {}
}

async function askConfirm() {
  step.value = 'confirm'
  hideInput()
  loading.value = true
  try {
    const data = await runPreview()
    const payable = state.pricingMode === 'VOUCHER'
      ? (data.payableAmount != null ? data.payableAmount : num(data.finalAmount) - num(data.voucherAmount))
      : (state.pricingMode === 'FIXED_PRICE' ? num(state.fixedPrice) : num(data.finalAmount))
    const headerText = state.headerDiscountType
      ? (state.headerDiscountType === 'PERCENT' ? (Number(state.headerDiscountValue)/10).toFixed(1).replace('.0','') + ' 折' : '减 ¥' + num(state.headerDiscountValue).toFixed(2))
      : ''
    const summary = {
      orderType: state.orderType,
      dealerName: state.dealerName,
      lines: state.lines.map(l => ({ ...l })),
      headerText,
      voucherText: state.voucherLabel || '',
      fixedPriceText: state.pricingMode === 'FIXED_PRICE' ? '¥' + num(state.fixedPrice).toFixed(2) : '',
      promoMessages: Array.isArray(data.promotionMessages) ? data.promotionMessages : [],
      payable,
      zeroOrder: state.orderType !== 'SALES'
    }
    state.address = null
    if (state.orderType === 'SALES') {
      try {
        const ar = await listDealerAddresses(state.dealerId)
        const list = Array.isArray(ar?.data) ? ar.data : (ar?.data?.list || [])
        const sorted = list.slice().sort((x, y) => Number(y.isDefault || 0) - Number(x.isDefault || 0))
        state.address = sorted[0] || null
      } catch (e) { state.address = null }
    }
    const addrWarn = state.orderType === 'SALES' && !state.address
      ? '<br/>⚠️ <b>该客户没有维护收货地址，提交/保存将失败，请先在 PC 端客户资料中维护地址。</b>'
      : (state.address ? '<br/>🚚 送货地址：<b>' + escapeHtml([state.address.province, state.address.city, state.address.district, state.address.address].filter(Boolean).join('')) + '</b>' : '')
    bot({
      title: '第九步：请确认订单',
      text: '以下是订单摘要，请核对：' + addrWarn,
      summary,
      options: [
        { key: 'submit', label: '1. ✅ 确认提交（进入审批）', action: 'finalize', value: 'submit', primary: true },
        { key: 'save', label: '2. 💾 保存草稿（稍后在订单列表提交）', action: 'finalize', value: 'save' },
        { key: 'cancel', label: '3. ❌ 取消，放弃本单', action: 'finalize', value: 'cancel', danger: true }
      ]
    })
  } catch (e) {
    const msg = e?.response?.data?.message || e.message || '计价失败'
    bot({
      text: '⚠️ 计价未通过：<b>' + escapeHtml(msg) + '</b><br/>请调整产品/数量/折扣后重新下单。',
      options: [
        { key: 'restart', label: '↺ 重新开始', action: 'restart', primary: true },
        { key: 'home', label: '返回首页', action: 'home' }
      ]
    })
  } finally {
    loading.value = false
  }
}

async function finalize(action) {
  if (action === 'cancel') {
    bot({
      text: '已取消本单，未创建任何订单。',
      options: [
        { key: 'again', label: '🔄 再下一单', action: 'restart', primary: true },
        { key: 'home', label: '返回首页', action: 'home' }
      ]
    })
    return
  }
  if (state.orderType === 'SALES' && !state.address) {
    showFailToast('该客户无收货地址，无法保存/提交，请先维护地址')
    return
  }
  loading.value = true
  try {
    const payload = buildPayload()
    if (state.orderType === 'SALES' && state.address) {
      payload.extra = {
        addressId: state.address.id,
        shipAddress: {
          addressName: state.address.addressName,
          contactName: state.address.contactName,
          phone: state.address.phone,
          province: state.address.province,
          city: state.address.city,
          district: state.address.district,
          address: state.address.address
        }
      }
    }
    const res = await createSalesOrderV43(payload)
    const newId = res?.data?.id
    if (!newId) throw new Error('创建订单未返回单号')
    if (action === 'submit') {
      try {
        await submitSalesOrderV43(newId)
        bot({
          text: '🎉 订单已提交成功，进入审批流程！<br/>单号：<b>' + escapeHtml(res.data.code || ('#' + newId)) + '</b>',
          options: [
            { key: 'view', label: '查看订单详情', action: 'view-order', value: newId, primary: true },
            { key: 'again', label: '🔄 再下一单', action: 'restart' },
            { key: 'home', label: '返回首页', action: 'home' }
          ]
        })
      } catch (e) {
        const msg = e?.response?.data?.message || '提交失败'
        bot({
          text: '订单已创建为草稿，但提交审批失败：<b>' + escapeHtml(msg) + '</b><br/>可在订单列表中重新提交。',
          options: [
            { key: 'view', label: '查看订单详情', action: 'view-order', value: newId, primary: true },
            { key: 'home', label: '返回首页', action: 'home' }
          ]
        })
      }
    } else {
      bot({
        text: '💾 订单已保存为草稿（单号：<b>' + escapeHtml(res.data.code || ('#' + newId)) + '</b>），可在「我的订单」中查看并提交。',
        options: [
          { key: 'view', label: '查看订单详情', action: 'view-order', value: newId, primary: true },
          { key: 'again', label: '🔄 再下一单', action: 'restart' },
          { key: 'orders', label: '前往订单列表', action: 'orders' }
        ]
      })
    }
  } catch (e) {
    const msg = e?.response?.data?.message || e.message || '创建失败'
    bot({
      text: '❌ ' + (action === 'submit' ? '提交' : '保存') + '失败：<b>' + escapeHtml(msg) + '</b>',
      options: [
        { key: 'restart', label: '↺ 重新开始', action: 'restart', primary: true },
        { key: 'home', label: '返回首页', action: 'home' }
      ]
    })
  } finally {
    loading.value = false
  }
}

function onSend() {
  const text = (inputValue.value || '').trim()
  if (!text) return
  if (text === '0') { userSay('0'); restart(); return }
  inputValue.value = ''
  switch (inputKind.value) {
    case 'product-search':
      userSay(text)
      doProductSearch(text)
      break
    case 'qty':
      onQtyInput(text)
      break
    case 'sample-reason':
      onSampleReason(text)
      break
    case 'line-discount-percent':
    case 'line-discount-amount':
      onLineDiscountValue(text)
      break
    case 'header-percent':
    case 'header-amount':
      userSay(text)
      onHeaderValue(text)
      break
    case 'fixed-price':
      onFixedPrice(text)
      break
    default:
      bot({ text: '请按提示操作，或点击右上角「↺ 重新开始」。' })
  }
}

function onOption(msg, opt) {
  if (msg.disabled) return
  switch (opt.action) {
    case 'restart':
      userSay('↺ 重新开始')
      restart()
      break
    case 'noop':
      break
    case 'page': {
      const all = listKind === 'product' ? productResults : listKind === 'dealer' ? dealers.value : voucherResults
      if (opt.value === 'next') listPage.value = Math.min(listPage.value + 1, listMaxPage(all.length))
      else listPage.value = Math.max(0, listPage.value - 1)
      rerenderPaged()
      break
    }
    case 'type':
      state.orderType = opt.value
      state.lines = []
      userSay(opt.label)
      askDealer()
      break
    case 'dealer': {
      const d = dealers.value.find(x => String(x.id) === String(opt.value))
      state.dealerId = opt.value
      state.dealerName = d ? d.label : ('#' + opt.value)
      userSay(opt.label)
      afterDealer()
      break
    }
    case 'askDealer':
      askDealer()
      break
    case 'product-search':
      hideInput()
      askProductSearch()
      break
    case 'product':
      userSay(opt.label)
      onProductPick(opt.value)
      break
    case 'after-lines':
      afterAllLines()
      break
    case 'line-discount':
      userSay(opt.label)
      onLineDiscountType(opt.value)
      break
    case 'add-more':
      userSay(opt.label)
      if (opt.value === 'yes') askProductSearch()
      else afterAllLines()
      break
    case 'header':
      userSay(opt.label)
      onHeaderChoice(opt.value)
      break
    case 'header-type':
      userSay(opt.label)
      onHeaderType(opt.value)
      break
    case 'ask-header':
      askHeaderDiscount()
      break
    case 'voucher':
      userSay(opt.label)
      onVoucherPick(opt.value)
      break
    case 'finalize':
      userSay(opt.label)
      finalize(opt.value)
      break
    case 'view-order':
      router.push('/mobile/orders/' + opt.value)
      break
    case 'orders':
      router.push('/mobile/orders')
      break
    case 'home':
      router.push('/mobile/home')
      break
  }
}

onMounted(async () => {
  start()
  await loadDealers()
})
</script>

<style scoped>
.smart-order { display: flex; flex-direction: column; height: 100vh; background: var(--dms-bg-page, #f7f8fa); }
.restart-link { font-size: 13px; color: var(--dms-color-primary, #1989fa); }
.chat-scroll { flex: 1; overflow-y: auto; padding: 12px 12px 16px; }
.msg-row { display: flex; gap: 8px; margin-bottom: 14px; align-items: flex-start; }
.msg-row.is-user { flex-direction: row-reverse; }
.avatar { flex: 0 0 34px; width: 34px; height: 34px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; color: #fff; }
.bot-avatar { background: var(--dms-color-primary, #1677ff); }
.user-avatar { background: var(--dms-blue-300); }
.bubble { max-width: 78%; padding: 10px 12px; border-radius: 10px; font-size: 14px; line-height: 1.6; word-break: break-word; }
.bubble.bot { background: #fff; color: var(--dms-text-1, #1f2329); border: 1px solid var(--van-gray-2, #ebedf0); border-top-left-radius: 2px; }
.bubble.user { background: var(--dms-color-primary, #1677ff); color: #fff; border-top-right-radius: 2px; }
.b-title { font-weight: 600; margin-bottom: 6px; }
.b-text :deep(b) { font-weight: 600; }
.opt-list { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; }
.opt-btn { text-align: left; }
.summary-card { margin-top: 10px; border-top: 1px dashed var(--van-gray-3, #dcdee0); padding-top: 8px; }
.sum-row { display: flex; justify-content: space-between; gap: 10px; font-size: 13px; padding: 2px 0; }
.sum-row .sum-r { text-align: right; color: var(--dms-text-2); }
.sum-lines { margin: 6px 0; }
.sum-line { background: var(--dms-bg-page, #f7f8fa); border-radius: 6px; padding: 6px 8px; margin-bottom: 6px; }
.sl-head { display: flex; gap: 6px; font-size: 13px; }
.sl-no { color: var(--dms-color-primary); font-weight: 600; flex: 0 0 auto; }
.sl-name { flex: 1; }
.sl-meta { display: flex; gap: 10px; font-size: 12px; color: var(--dms-text-3, #86909c); margin-top: 2px; padding-left: 22px; }
.promo-lines { margin: 6px 0; padding: 6px 8px; background: #fff7e8; border-radius: 6px; }
.promo-line { font-size: 12px; color: #ad6800; line-height: 1.6; }
.sum-total { display: flex; justify-content: space-between; align-items: center; margin-top: 6px; padding-top: 8px; border-top: 1px solid var(--van-gray-3, #dcdee0); font-weight: 600; }
.sum-amt { color: var(--dms-color-danger, #ee0a24); font-size: 18px; }

.page-info { margin-top: 8px; font-size: 12px; color: var(--van-text-color-3, #969799); }
.qty-bar { flex: 0 0 auto; background: #fff; border-top: 1px solid var(--van-gray-2, #ebedf0); padding: 10px 12px 4px; }
.qty-stepper { display: flex; align-items: center; justify-content: center; gap: 20px; margin-bottom: 8px; }
.qty-val { font-size: 24px; font-weight: 700; color: var(--dms-color-primary, #1677ff); min-width: 60px; text-align: center; }
.qty-quick { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; margin-bottom: 10px; }
.input-bar { flex: 0 0 auto; padding: 8px 12px calc(8px + env(safe-area-inset-bottom)); background: #fff; border-top: 1px solid var(--van-gray-2, #ebedf0); }
.input-hint { text-align: center; font-size: 11px; color: var(--dms-text-4, #a9aeb8); margin-top: 4px; }
</style>
