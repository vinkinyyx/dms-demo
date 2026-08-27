<template>
  <div class="m-register">
    <van-nav-bar title="客户注册" left-arrow @click-left="$router.back()" />

    <div class="reg-head">
      <div class="reg-title">经销商自助注册</div>
      <div class="reg-sub">填写企业资料并上传资质，审核通过后账号自动开通</div>
    </div>

    <van-form @submit="onSubmit">
      <van-cell-group inset title="登录账号" style="margin-top:12px">
        <van-field
          v-model="form.phone"
          label="手机号"
          type="tel"
          maxlength="11"
          placeholder="登录手机号"
          :rules="[{ required: true, message: '请输入手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }]"
        />
        <van-field
          v-model="form.password"
          label="登录密码"
          type="password"
          placeholder="6-64 位登录密码"
          :rules="[{ required: true, message: '请输入密码' }, { validator: v => v.length >= 6 && v.length <= 64, message: '密码长度需在 6-64 位之间' }]"
        />
        <van-field
          v-model="form.email"
          label="邮箱"
          type="text"
          placeholder="选填"
          :rules="[{ validator: v => !v || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v), message: '邮箱格式不正确' }]"
        />
      </van-cell-group>

      <van-cell-group inset title="企业信息" style="margin-top:12px">
        <van-field v-model="form.companyName" label="公司名称" required placeholder="营业执照全称" :rules="[{ required: true, message: '请输入公司名称' }]" />
        <van-field v-model="form.uscNo" label="信用代码" placeholder="统一社会信用代码（选填）" />
        <van-field v-model="form.legalPerson" label="法人代表" placeholder="选填" />
        <van-field v-model="form.regAddress" label="注册地址" type="textarea" rows="2" autosize placeholder="企业注册地址（选填）" />
      </van-cell-group>

      <van-cell-group inset title="联系人" style="margin-top:12px">
        <van-field v-model="form.contactName" label="联系人" required placeholder="主要联系人姓名" :rules="[{ required: true, message: '请输入联系人姓名' }]" />
        <van-field v-model="form.contactPhone" label="联系电话" type="tel" maxlength="11" placeholder="联系人手机号" :rules="[{ validator: v => !v || /^1[3-9]\d{9}$/.test(v), message: '手机号格式不正确' }]" />
      </van-cell-group>

      <van-cell-group inset :title="'收货地址（至少 ' + (addresses.length ? '1 项' : '1 项') + '）'" style="margin-top:12px">
        <div v-for="(a, idx) in addresses" :key="idx" class="addr-block">
          <div class="addr-head">
            <span class="addr-idx">地址 {{ idx + 1 }}<van-tag v-if="a.isDefault" plain type="primary" size="mini" style="margin-left:6px">默认</van-tag></span>
            <van-button size="mini" type="danger" plain @click="removeAddress(idx)" :disabled="addresses.length <= 1">删除</van-button>
          </div>
          <van-field v-model="a.addressName" label="地址名称" placeholder="如：总部仓 / 门店" />
          <van-field v-model="a.contactName" label="收货人" placeholder="收货人姓名" />
          <van-field v-model="a.phone" label="收货电话" type="tel" maxlength="11" placeholder="收货人手机号" />
          <van-field v-model="a.province" label="省" placeholder="省" />
          <van-field v-model="a.city" label="市" placeholder="市" />
          <van-field v-model="a.district" label="区/县" placeholder="区/县" />
          <van-field v-model="a.address" label="详细地址" placeholder="街道、门牌号" />
          <van-field v-model="a.postalCode" label="邮编" placeholder="选填" />
          <van-field label="设为默认">
            <template #input>
              <van-switch v-model="a.isDefault" size="20" @change="onDefaultChange(idx)" />
            </template>
          </van-field>
        </div>
        <div style="padding:10px 16px">
          <van-button block plain icon="plus" @click="addAddress">添加收货地址</van-button>
        </div>
      </van-cell-group>

      <van-cell-group inset title="资质附件" style="margin-top:12px">
        <van-field label="营业执照等">
          <template #input>
            <van-uploader v-model="fileList" :after-read="afterRead" multiple :max-count="9" accept="image/*,.pdf" />
          </template>
        </van-field>
        <div style="padding:0 16px 12px;color:var(--van-text-color-3);font-size:12px;line-height:1.6">
          可上传营业执照、经营许可等资料（图片或 PDF），用于审核。
        </div>
      </van-cell-group>

      <div style="margin:20px 16px 8px">
        <van-button round block type="primary" native-type="submit" :loading="submitting">提交注册申请</van-button>
      </div>
    </van-form>

    <van-dialog
      v-model:show="showSuccess"
      title="提交成功"
      confirm-button-text="返回登录"
      @confirm="goLogin"
    >
      <div style="padding:16px;line-height:1.8;font-size:14px;text-align:center">
        资料已提交，审核通过后账号自动开通。<br />审核结果将通过短信通知，请留意。
      </div>
    </van-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showFailToast } from 'vant'
import { customerRegister, uploadRegisterFile } from '@/api/mobileV43'

const router = useRouter()
const submitting = ref(false)
const showSuccess = ref(false)
const fileList = ref([])
const attachments = ref([])

const form = reactive({
  phone: '',
  password: '',
  email: '',
  companyName: '',
  uscNo: '',
  legalPerson: '',
  contactName: '',
  contactPhone: '',
  regAddress: ''
})

function makeAddress(isDefault = false) {
  return {
    addressName: '', contactName: '', phone: '', province: '', city: '',
    district: '', address: '', postalCode: '', isDefault
  }
}
const addresses = ref([makeAddress(true)])

function addAddress() {
  addresses.value.push(makeAddress(false))
}
function removeAddress(idx) {
  addresses.value.splice(idx, 1)
  if (addresses.value.length && !addresses.value.some(a => a.isDefault)) {
    addresses.value[0].isDefault = true
  }
}
function onDefaultChange(idx) {
  addresses.value.forEach((a, i) => { a.isDefault = i === idx })
}

async function afterRead(file) {
  const files = Array.isArray(file) ? file : [file]
  for (const f of files) {
    f.status = 'uploading'
    f.message = '上传中'
    try {
      const fd = new FormData()
      fd.append('file', f.file)
      const res = await uploadRegisterFile(fd)
      const data = res?.data || {}
      f.url = data.url
      f.status = 'done'
      attachments.value.push({ name: data.originalName || f.file?.name, url: data.url, fileId: data.fileId, type: f.file?.type })
    } catch (e) {
      f.status = 'failed'
      f.message = '上传失败'
      showFailToast('附件上传失败')
    }
  }
}

function validateAddresses() {
  for (let i = 0; i < addresses.value.length; i++) {
    const a = addresses.value[i]
    if (!a.address || !a.contactName) {
      return `第 ${i + 1} 个收货地址请至少填写收货人和详细地址`
    }
  }
  return null
}

function goLogin() {
  router.replace('/mobile/login')
}

async function onSubmit() {
  const addrErr = validateAddresses()
  if (addrErr) { showFailToast(addrErr); return }
  if (fileList.value.some(f => f.status === 'uploading')) { showFailToast('附件正在上传，请稍候'); return }

  submitting.value = true
  try {
    const payload = {
      registerName: form.contactName || form.companyName,
      phone: form.phone,
      email: form.email || null,
      password: form.password,
      companyName: form.companyName,
      uscNo: form.uscNo || null,
      legalPerson: form.legalPerson || null,
      contactName: form.contactName || null,
      contactPhone: form.contactPhone || null,
      regAddress: form.regAddress || null,
      addresses: addresses.value.map(a => ({
        addressName: a.addressName || '收货地址',
        contactName: a.contactName,
        phone: a.phone || form.phone,
        province: a.province || null,
        city: a.city || null,
        district: a.district || null,
        address: a.address,
        postalCode: a.postalCode || null,
        isDefault: !!a.isDefault
      })),
      attachments: attachments.value
    }
    await customerRegister(payload)
    showSuccess.value = true
  } catch (e) {
    showFailToast(e?.response?.data?.message || e?.message || '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.m-register { min-height: 100vh; background: var(--van-background, #f4f6fa); padding-bottom: 40px; }
.reg-head { padding: 20px 20px 6px; }
.reg-title { font-size: 20px; font-weight: 700; color: var(--van-text-color); }
.reg-sub { font-size: 13px; color: var(--van-text-color-2); margin-top: 6px; line-height: 1.6; }
.addr-block { border-bottom: 1px dashed var(--van-gray-3); padding: 8px 0; }
.addr-block:last-of-type { border-bottom: 0; }
.addr-head { display: flex; justify-content: space-between; align-items: center; padding: 8px 16px 2px; }
.addr-idx { font-size: 13px; font-weight: 600; color: var(--van-text-color-2); }
.m-register :deep(.van-field__label) { width: 84px !important; flex: none; font-size: 13px; }
.m-register :deep(.van-button--primary) {
  height: 46px; border-radius: 6px; font-size: 16px; font-weight: 600;
  background: var(--dms-color-primary, #1989fa);
}
</style>
