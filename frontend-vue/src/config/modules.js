const S_ACTIVE = [{ value: 'active', label: '启用' }, { value: 'inactive', label: '停用' }]
const S_ACTIVE_BLOCK = [...S_ACTIVE, { value: 'blocked', label: '冻结' }]
const UNITS = [{ value: '个', label: '个' }, { value: '盒', label: '盒' }, { value: '箱', label: '箱' }, { value: '支', label: '支' }, { value: '瓶', label: '瓶' }, { value: '包', label: '包' }, { value: '套', label: '套' }]
const CURRENCIES = [{ value: 'CNY', label: '人民币' }, { value: 'USD', label: '美元' }, { value: 'EUR', label: '欧元' }]

// 获取数据字典选项
import { getDictOptions } from '@/utils/dict'

const products = {
  key: 'products', title: '产品管理', api: '/api/products', detailable: true, importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '产品编码', w: 130, filter: { type: 'text' } }, 
    { k: 'nameCn', l: '中文名称', filter: { type: 'text' } },
    { k: 'productType', l: '产品类型', w: 100, filter: { type: 'select', options: getDictOptions('product_type') } },
    { k: 'categoryName', l: '产品分类', w: 120, filter: { type: 'text' } },
    { k: 'spec', l: '规格型号', w: 120, filter: { type: 'text' } }, 
    { k: 'unit', l: '单位', w: 70, filter: { type: 'select', options: UNITS } }, 
    { k: 'currentPrice', l: '参考单价', w: 100, filter: { type: 'number' } },
    { k: 'taxRate', l: '税率', w: 70, filter: { type: 'number' } }, 
    { k: 'udiRequired', l: 'UDI追溯', w: 80, filter: { type: 'select', options: [{ value: true, label: '是' }, { value: false, label: '否' }] } },
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'code', label: '产品编码', required: true, group: '基本信息', placeholder: '如 PROD-XXXXX' },
    { key: 'nameCn', label: '中文名称', required: true, group: '基本信息' },
    { key: 'nameEn', label: '英文名称', group: '基本信息' },
    { key: 'productType', label: '产品类型', type: 'select', required: true, group: '基本信息', options: getDictOptions('product_type') },
    { key: 'categoryId', label: '产品分类', required: true, group: '基本信息', picker: 'categories' },
    { key: 'spec', label: '规格型号', group: '规格与价格' },
    { key: 'unit', label: '单位', type: 'select', group: '规格与价格', value: '个', options: UNITS },
    { key: 'currentPrice', label: '参考单价', type: 'number', group: '规格与价格' },
    { key: 'taxRate', label: '税率(如 0.13)', type: 'number', group: '规格与价格', value: 0.13 },
    { key: 'udiRequired', label: '需要UDI追溯', type: 'boolean', group: '医疗器械', value: true },
    { key: 'warnMonths', label: '临期预警(月)', type: 'number', group: '医疗器械', value: 3 },
    { key: 'safetyQty', label: '安全库存', type: 'number', group: '库存参数', value: 10 },
    { key: 'minOrderQty', label: '最小订购量', type: 'number', group: '库存参数', value: 1 },
    { key: 'status', label: '状态', type: 'select', group: '状态', value: 'active', options: S_ACTIVE }
  ]
}

const categories = {
  key: 'categories', title: '产品分类', api: '/api/product-categories', detailable: true, importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '分类编码', w: 140, filter: { type: 'text' } }, 
    { k: 'name', l: '分类名称', filter: { type: 'text' } }, 
    { k: 'sortOrder', l: '排序', w: 80, filter: { type: 'number' } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'code', label: '分类编码', required: true, group: '基本信息' },
    { key: 'name', label: '分类名称', required: true, group: '基本信息' },
    { key: 'parentId', label: '父分类', group: '基本信息', picker: 'categories' },
    { key: 'sortOrder', label: '排序', type: 'number', value: 1, group: '基本信息' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '状态', options: S_ACTIVE }
  ]
}

const dealers = {
  key: 'dealers', title: '经销商管理', api: '/api/dealers', detailable: true, importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '经销商编码', w: 130, filter: { type: 'text' } }, 
    { k: 'name', l: '经销商名称', filter: { type: 'text' } }, 
    { k: 'level', l: '级别', w: 70, filter: { type: 'select', options: [{ value: 'VIP', label: 'VIP' }, { value: 'LEVEL_1', label: '一级' }, { value: 'LEVEL_2', label: '二级' }, { value: 'LEVEL_3', label: '三级' }, { value: 'NORMAL', label: '普通' }] } }, 
    { k: 'legalPerson', l: '法人', w: 110, filter: { type: 'text' } }, 
    { k: 'contactPhone', l: '联系电话', w: 120, filter: { type: 'text' } }, 
    { k: 'gspStatus', l: 'GSP资质', w: 90, filter: { type: 'select', options: [{ value: 'active', label: '有效' }, { value: 'expired', label: '已过期' }, { value: 'none', label: '无' }] } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE_BLOCK } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'code', label: '经销商编码', required: true, group: '基本信息' },
    { key: 'name', label: '经销商名称', required: true, group: '基本信息' },
    { key: 'level', label: '级别', type: 'select', required: true, group: '基本信息', value: 'T1', options: [{ value: 'T1', label: '一级' }, { value: 'T2', label: '二级' }] },
    { key: 'regionId', label: '所属区域', group: '基本信息', picker: 'regions' },
    { key: 'legalPerson', label: '法定代表人', group: '工商信息' },
    { key: 'uscNo', label: '统一社会信用代码', group: '工商信息' },
    { key: 'regAddress', label: '注册地址', group: '工商信息' },
    { key: 'foundedAt', label: '成立日期', type: 'date', group: '工商信息' },
    { key: 'businessScope', label: '经营范围', type: 'textarea', group: '工商信息' },
    { key: 'contactName', label: '联系人', group: '联系信息' },
    { key: 'contactPhone', label: '联系电话', group: '联系信息' },
    { key: 'contactEmail', label: '邮箱', type: 'email', group: '联系信息' },
    { key: 'gspStatus', label: 'GSP资质状态', type: 'select', group: '资质', value: 'active', options: [{ value: 'active', label: '有效' }, { value: 'expired', label: '已过期' }, { value: 'none', label: '无' }] },
    { key: 'gspExpire', label: 'GSP到期日', type: 'date', group: '资质' },
    { key: 'status', label: '状态', type: 'select', group: '状态', value: 'active', options: S_ACTIVE_BLOCK }
  ]
}

const hospitals = {
  key: 'hospitals', title: '医院/终端', api: '/api/hospitals', importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '医院编码', w: 130, filter: { type: 'text' } }, 
    { k: 'name', l: '医院名称', filter: { type: 'text' } }, 
    { k: 'level', l: '等级', w: 90, filter: { type: 'select', options: [{ value: '三甲', label: '三级甲等' }, { value: '三乙', label: '三级乙等' }, { value: '二甲', label: '二级甲等' }, { value: '二乙', label: '二级乙等' }, { value: '一级', label: '一级' }, { value: '未定', label: '未定级' }] } }, 
    { k: 'contactPhone', l: '联系电话', w: 120, filter: { type: 'text' } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'code', label: '医院编码', required: true, group: '基本信息' },
    { key: 'name', label: '医院名称', required: true, group: '基本信息' },
    { key: 'level', label: '医院等级', type: 'select', group: '基本信息', options: [{ value: '三甲', label: '三级甲等' }, { value: '三乙', label: '三级乙等' }, { value: '二甲', label: '二级甲等' }, { value: '二乙', label: '二级乙等' }, { value: '一级', label: '一级' }, { value: '未定', label: '未定级' }] },
    { key: 'regionId', label: '所属区域', group: '基本信息', picker: 'regions' },
    { key: 'contactName', label: '联系人', group: '联系信息' },
    { key: 'contactPhone', label: '联系电话', group: '联系信息' },
    { key: 'address', label: '地址', type: 'textarea', group: '联系信息' },
    { key: 'status', label: '状态', type: 'select', group: '状态', value: 'active', options: S_ACTIVE }
  ]
}

const warehouses = {
  key: 'warehouses', title: '仓库管理', api: '/api/warehouses', importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '仓库编码', w: 130, filter: { type: 'text' } }, 
    { k: 'name', l: '仓库名称', filter: { type: 'text' } }, 
    { k: 'type', l: '类型', w: 100, filter: { type: 'select', options: getDictOptions('warehouse_type') } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],  form: [
    { key: 'code', label: '仓库编码', required: true, group: '基本信息' },
    { key: 'name', label: '仓库名称', required: true, group: '基本信息' },
    { key: 'type', label: '仓库类型', type: 'select', required: true, group: '基本信息', value: 'main', options: [{ value: 'main', label: '主仓库' }, { value: 'sub', label: '分仓库' }, { value: 'hospital', label: '医院寄售仓' }] },
    { key: 'dealerId', label: '所属经销商', picker: 'dealers', group: '归属' },
    { key: 'hospitalId', label: '所属医院(寄售仓)', picker: 'hospitals', group: '归属' },
    { key: 'address', label: '仓库地址', type: 'textarea', group: '详情' },
    { key: 'status', label: '状态', type: 'select', group: '状态', value: 'active', options: S_ACTIVE }
  ]
}

const regions = {
  key: 'regions', title: '区域管理', api: '/api/regions', importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '区域编码', w: 140, filter: { type: 'text' } }, 
    { k: 'name', l: '区域名称', filter: { type: 'text' } }, 
    { k: 'level', l: '级别', w: 80, filter: { type: 'number' } }, 
    { k: 'sortOrder', l: '排序', w: 80, filter: { type: 'number' } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'code', label: '区域编码', required: true, group: '基本信息' },
    { key: 'name', label: '区域名称', required: true, group: '基本信息' },
    { key: 'level', label: '级别', type: 'number', value: 1, group: '基本信息' },
    { key: 'parentId', label: '父区域', picker: 'regions', group: '基本信息' },
    { key: 'sortOrder', label: '排序', type: 'number', value: 1, group: '基本信息' }
  ]
}

const materials = {
  key: 'materials', title: '物料管理', api: '/api/materials', importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '物料编码', w: 130, filter: { type: 'text' } }, 
    { k: 'nameCn', l: '物料名称', filter: { type: 'text' } }, 
    { k: 'productId', l: '关联产品', w: 100, format: 'productName' }, 
    { k: 'spec', l: '规格型号', w: 140, filter: { type: 'text' } }, 
    { k: 'unit', l: '单位', w: 60, filter: { type: 'select', options: UNITS } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'code', label: '物料编码', required: true, group: '基本信息' },
    { key: 'nameCn', label: '物料名称', required: true, group: '基本信息' },
    { key: 'productId', label: '关联产品', group: '基本信息', picker: 'products' },
    { key: 'spec', label: '规格型号', group: '基本信息' },
    { key: 'unit', label: '单位', type: 'select', group: '基本信息', value: '个', options: UNITS },
    { key: 'remark', label: '备注', type: 'textarea', group: '其它' },
    { key: 'status', label: '状态', type: 'select', group: '状态', value: 'active', options: S_ACTIVE }
  ]
}

const suppliers = {
  key: 'suppliers', title: '供应商管理', api: '/api/suppliers', importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '供应商编码', w: 130, filter: { type: 'text' } }, 
    { k: 'name', l: '供应商名称', filter: { type: 'text' } }, 
    { k: 'level', l: '供应商等级', w: 90, filter: { type: 'select', options: getDictOptions('supplier_level') } }, 
    { k: 'contactPerson', l: '联系人', w: 100, filter: { type: 'text' } }, 
    { k: 'contactPhone', l: '联系电话', w: 120, filter: { type: 'text' } }, 
    { k: 'taxNo', l: '税号', w: 160, filter: { type: 'text' } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE_BLOCK } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'code', label: '供应商编码', required: true },
    { key: 'name', label: '供应商名称', required: true },
    { key: 'level', label: '供应商等级', type: 'select', required: true, options: getDictOptions('supplier_level') },
    { key: 'contactPerson', label: '联系人' },
    { key: 'contactPhone', label: '联系电话' },
    { key: 'address', label: '地址', full: true },
    { key: 'bankAccount', label: '银行账号' },
    { key: 'taxNo', label: '税号' },
    { key: 'remark', label: '备注', type: 'textarea', full: true },
    { key: 'status', label: '状态', type: 'select', value: 'active', options: S_ACTIVE_BLOCK }
  ]
}

const productPrices = {
  key: 'product-prices', title: '产品价格', api: '/api/product-prices', importable: true, exportable: true,
  cols: [
    { k: 'id', l: 'ID', w: 60, filter: { type: 'number' } }, 
    { k: 'productCode', l: '产品编码', w: 130, link: { menu: 'products', valueKey: 'productId' }, filter: { type: 'text' } },
    { k: 'productName', l: '产品名称', filter: { type: 'text' } }, 
    { k: 'partnerType', l: '范围', w: 90, filter: { type: 'select', options: [{ value: 'GLOBAL', label: '全局' }, { value: 'DEALER', label: '按经销商' }, { value: 'SUPPLIER', label: '按供应商' }] } }, 
    { k: 'partnerName', l: '伙伴', w: 120, filter: { type: 'text' } },
    { k: 'purchasePrice', l: '采购价', w: 110, filter: { type: 'number' } }, 
    { k: 'salesPrice', l: '销售价', w: 110, filter: { type: 'number' } }, 
    { k: 'currency', l: '货币', w: 70, filter: { type: 'select', options: CURRENCIES } }, 
    { k: 'status', l: '状态', w: 70, filter: { type: 'select', options: S_ACTIVE } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'productId', label: '产品', required: true, type: 'product-picker' },
    { key: 'partnerType', label: '范围', type: 'select', required: true, options: [{ value: 'GLOBAL', label: '全局' }, { value: 'DEALER', label: '按经销商' }, { value: 'SUPPLIER', label: '按供应商' }] },
    { key: 'partnerId', label: '伙伴ID（GLOBAL 填 0）', type: 'number' },
    { key: 'purchasePrice', label: '采购价', type: 'number', required: true },
    { key: 'salesPrice', label: '销售价', type: 'number', required: true },
    { key: 'currency', label: '货币', type: 'select', value: 'CNY', options: CURRENCIES },
    { key: 'effectiveDate', label: '生效日期', type: 'date' },
    { key: 'expireDate', label: '失效日期', type: 'date' },
    { key: 'status', label: '状态', type: 'select', value: 'active', options: S_ACTIVE }
  ]
}

const contractApps = {
  key: 'contract-apps', title: '合同申请', api: '/api/contract-applications',
  maxActions: 2,
  actions: [
    { label: '提交审批', method: 'POST', path: '/submit', confirm: '确认提交此申请？', type: 'warning' },
    { label: '审批通过', method: 'POST', path: '/approve', confirm: '审批通过后将自动生成合同，是否继续？', type: 'success' }
  ],
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '申请编号', w: 160, filter: { type: 'text' }, link: { menu: 'contracts', valueKey: 'id' } }, 
    { k: 'applicationType', l: '申请类型', w: 90, filter: { type: 'select', options: [{ value: 'NEW', label: '新签' }, { value: 'MODIFY', label: '变更' }, { value: 'RENEW', label: '续签' }, { value: 'TERMINATE', label: '终止' }] } }, 
    { k: 'contractCategory', l: '合同分类', w: 100, filter: { type: 'select', options: [{ value: 'SALES', label: '销售合同' }, { value: 'AUTHORIZATION', label: '授权合同' }, { value: 'DISTRIBUTION', label: '经销合同' }] } }, 
    { k: 'dealerName', l: '经销商', filter: { type: 'text' } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'PENDING_APPROVAL', label: '待审批' }, { value: 'APPROVED', label: '已批准' }, { value: 'REJECTED', label: '已驳回' }] } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'applicationType', label: '申请类型', type: 'select', required: true, group: '基本信息', value: 'NEW', options: [{ value: 'NEW', label: '新签' }, { value: 'MODIFY', label: '变更' }, { value: 'RENEW', label: '续签' }, { value: 'TERMINATE', label: '终止' }] },
    { key: 'contractCategory', label: '合同分类', type: 'select', required: true, group: '基本信息', value: 'SALES', options: [{ value: 'SALES', label: '销售合同' }, { value: 'AUTHORIZATION', label: '授权合同' }, { value: 'DISTRIBUTION', label: '经销合同' }] },
    { key: 'dealerId', label: '经销商', required: true, picker: 'dealers', group: '基本信息' },
    { key: 'validFrom', label: '生效开始', type: 'date', group: '有效期' },
    { key: 'validTo', label: '生效结束', type: 'date', group: '有效期' },
    { key: 'remark', label: '备注说明', type: 'textarea', group: '其它' }
  ]
}

const contracts = {
  key: 'contracts', title: '合同', api: '/api/contracts', readonly: true, detailable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '合同编号', w: 180, filter: { type: 'text' } }, 
    { k: 'category', l: '分类', w: 110, filter: { type: 'text' } }, 
    { k: 'dealerName', l: '经销商', filter: { type: 'text' } }, 
    { k: 'validFrom', l: '生效', w: 110, filter: { type: 'date' } }, 
    { k: 'validTo', l: '截止', w: 110, filter: { type: 'date' } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'ACTIVE', label: '生效' }, { value: 'EXPIRED', label: '到期' }, { value: 'TERMINATED', label: '终止' }] } }
  ]
}

const authorizations = {
  key: 'authorizations', title: '授权管理', api: '/api/authorizations', detailable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '授权编号', w: 160, filter: { type: 'text' } }, 
    { k: 'dealerName', l: '经销商', filter: { type: 'text' } }, 
    { k: 'validFrom', l: '生效', w: 110, filter: { type: 'date' } }, 
    { k: 'validTo', l: '截止', w: 110, filter: { type: 'date' } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'active', label: '启用' }, { value: 'suspended', label: '挂起' }, { value: 'expired', label: '已过期' }] } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'dealerId', label: '经销商', required: true, type: 'picker', picker: 'dealers', group: '授权主体' },
    { key: 'categoryIds', label: '授权产品分类（多选）', type: 'multiselect', picker: { resource: 'categories' }, required: true, group: '授权范围' },
    { key: 'terminalIds', label: '授权医院/终端（多选）', type: 'multiselect', picker: { resource: 'hospitals' }, required: true, group: '授权范围' },
    { key: 'validFrom', label: '生效开始', type: 'date', required: true, group: '有效期' },
    { key: 'validTo', label: '生效结束', type: 'date', required: true, group: '有效期' },
    { key: 'contractId', label: '关联合同（可选）', picker: 'contracts', group: '其它' },
    { key: 'remark', label: '备注', type: 'textarea', group: '其它' },
    { key: 'status', label: '状态', type: 'select', group: '状态', value: 'active', options: [{ value: 'active', label: '启用' }, { value: 'suspended', label: '挂起' }, { value: 'expired', label: '已过期' }] }
  ]
}

const promotions = {
  key: 'promotions', title: '促销规则', api: '/api/promotions',
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '编码', w: 130, filter: { type: 'text' } }, 
    { k: 'name', l: '名称', filter: { type: 'text' } }, 
    { k: 'promoType', l: '类型', w: 120, filter: { type: 'select', options: [{ value: 'MOQ', label: '起订量(MOQ)' }, { value: 'FULL_REDUCTION', label: '满减' }] } }, 
    { k: 'priority', l: '优先级', w: 70, filter: { type: 'number' } }, 
    { k: 'validFrom', l: '开始', w: 110, filter: { type: 'date' } }, 
    { k: 'validTo', l: '结束', w: 110, filter: { type: 'date' } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: [{ value: 'draft', label: '草稿' }, { value: 'active', label: '启用' }, { value: 'paused', label: '暂停' }] } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'code', label: '促销编码', required: true, group: '基本信息' },
    { key: 'name', label: '促销名称', required: true, group: '基本信息' },
    { key: 'promoType', label: '促销类型', type: 'select', required: true, value: 'MOQ', group: '基本信息', options: [{ value: 'MOQ', label: '起订量(MOQ)' }, { value: 'FULL_REDUCTION', label: '满减' }] },
    { key: 'priority', label: '优先级(越大越优先)', type: 'number', value: 10, group: '规则' },
    { key: 'exclusive', label: '排他', type: 'boolean', value: true, group: '规则' },
    { key: 'validFrom', label: '生效开始', type: 'date', group: '有效期' },
    { key: 'validTo', label: '生效结束', type: 'date', group: '有效期' },
    { key: 'status', label: '状态', type: 'select', value: 'draft', group: '状态', options: [{ value: 'draft', label: '草稿' }, { value: 'active', label: '启用' }, { value: 'paused', label: '暂停' }] },
    { key: 'description', label: '说明', type: 'textarea', group: '其它' }
  ]
}

const LINE_STOCK = [
  { k: 'productId', l: '产品', type: 'picker', picker: 'products', format: 'productName' },
  { k: 'batchNo', l: '批次号' }, { k: 'serialNo', l: '序列号' }, { k: 'qty', l: '数量', type: 'number' }
]
const LINE_ORDER = [
  { k: 'productId', l: '产品', type: 'picker', picker: 'products', format: 'productName', required: true },
  { k: 'qty', l: '数量', type: 'number', required: true },
  { k: 'unitPrice', l: '单价', type: 'number' },
  { k: 'taxRate', l: '税率', type: 'number' }
]

const orders = {
  key: 'orders', title: '销售订单', api: '/api/sales-orders', detailable: true, noDelete: true, editableWhen: ['DRAFT'], pageSize: 30,
  statusActions: [
    { label: '提交审批', when: ['DRAFT'], method: 'POST', path: '/submit', type: 'primary', confirm: '确认提交此销售订单进入审批？' },
    { label: '审批通过', when: ['SUBMITTED'], method: 'POST', path: '/approve', type: 'success', confirm: '确认审批通过此销售订单？（将自动生成销售出库草稿）' },
    { label: '驳回', when: ['SUBMITTED'], method: 'POST', path: '/reject', type: 'danger', confirm: '确认驳回此销售订单？' },
    { label: '取消', when: ['DRAFT', 'APPROVED'], method: 'POST', path: '/cancel', type: 'warning', confirm: '确认取消此销售订单？' }
  ],
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '销售单号', w: 170, filter: { type: 'text' } }, 
    { k: 'orderType', l: '类型', w: 90, filter: { type: 'select', options: [{ value: 'NORMAL', label: '常规销售' }, { value: 'URGENT', label: '紧急销售' }] } }, 
    { k: 'dealerName', l: '经销商', filter: { type: 'text' } }, 
    { k: 'warehouseName', l: '发货仓库', w: 120, filter: { type: 'text' } }, 
    { k: 'amountInclTax', l: '含税金额', w: 120, filter: { type: 'number' } }, 
    { k: 'finalAmount', l: '实付金额', w: 120, filter: { type: 'number' } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'SUBMITTED', label: '待审批' }, { value: 'APPROVED', label: '已审批' }, { value: 'SHIPPING', label: '发货中' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }, { value: 'REJECTED', label: '已驳回' }] } }, 
    { k: 'auditUserName', l: '审核人', w: 90, filter: { type: 'text' } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'orderType', label: '销售类型', type: 'select', required: true, value: 'NORMAL', group: '销售信息', options: [{ value: 'NORMAL', label: '常规销售' }, { value: 'URGENT', label: '紧急销售' }] },
    { key: 'dealerId', label: '经销商', required: true, picker: 'dealers', group: '销售信息' },
    { key: 'warehouseId', label: '发货仓库', required: true, picker: 'warehouses', group: '销售信息' },
    { key: 'expectedDate', label: '期望发货日期', type: 'date', group: '销售信息' },
    { key: 'remark', label: '销售备注', type: 'textarea', group: '其它' },
    { key: 'lines', type: 'lines', label: '销售明细', required: true, group: '销售明细', cols: LINE_ORDER }
  ]
}

const salesReturns = {
  key: 'sales-returns', title: '销退订单', api: '/api/orders', extraParams: { isRed: true }, apiCreate: '/api/orders', detailable: true,
  statusActions: [
    { label: '提交审批', when: ['DRAFT'], method: 'POST', path: '/submit', type: 'primary', confirm: '确认提交此销退订单进入审批？' },
    { label: '审批通过', when: ['SUBMITTED'], method: 'POST', path: '/approve', type: 'success', confirm: '确认审批通过此销退订单？' },
    { label: '驳回', when: ['SUBMITTED'], method: 'POST', path: '/reject', type: 'danger', confirm: '确认驳回此销退订单？' },
    { label: '取消', when: ['DRAFT', 'SUBMITTED'], method: 'POST', path: '/cancel', type: 'warning', confirm: '确认取消此销退订单？' }
  ],
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '销退单号', w: 170, filter: { type: 'text' } }, 
    { k: 'orderType', l: '类型', w: 90, filter: { type: 'select', options: [{ value: 'RETURN', label: '销退' }, { value: 'EXCHANGE', label: '换货' }] } }, 
    { k: 'dealerName', l: '经销商', filter: { type: 'text' } }, 
    { k: 'finalAmount', l: '金额', w: 120, filter: { type: 'number' } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'SUBMITTED', label: '待审批' }, { value: 'APPROVED', label: '已审批' }, { value: 'RECEIVING', label: '收货中' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }, { value: 'REJECTED', label: '已驳回' }] } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'isRed', label: '红字标记', type: 'boolean', value: true, readonly: true, group: '销退信息' },
    { key: 'orderType', label: '销退类型', type: 'select', required: true, value: 'RETURN', group: '销退信息', options: [{ value: 'RETURN', label: '销退' }, { value: 'EXCHANGE', label: '换货' }] },
    { key: 'refOrderId', label: '原销售订单', required: true, picker: 'orders', group: '销退信息' },
    { key: 'dealerId', label: '经销商', required: true, picker: 'dealers', group: '销退信息' },
    { key: 'remark', label: '销退原因', type: 'textarea', required: true, group: '其它' },
    { key: 'lines', type: 'lines', label: '销退明细', required: true, group: '销退明细', cols: LINE_ORDER }
  ]
}

const purchaseOrders = {
  key: 'purchase-orders', title: '采购订单', api: '/api/purchase-orders', detailable: true, noDelete: true, editableWhen: ['DRAFT'], pageSize: 30,
  statusActions: [
    { label: '提交审批', when: ['DRAFT'], method: 'POST', path: '/submit', type: 'primary', confirm: '确认提交此采购订单进入审批？' },
    { label: '审批通过', when: ['SUBMITTED'], method: 'POST', path: '/approve', type: 'success', confirm: '确认审批通过此采购订单？（将自动生成收货入库草稿）' },
    { label: '驳回', when: ['SUBMITTED'], method: 'POST', path: '/reject', type: 'danger', confirm: '确认驳回此采购订单？' },
    { label: '取消', when: ['DRAFT', 'APPROVED'], method: 'POST', path: '/cancel', type: 'warning', confirm: '确认取消此采购订单？' }
  ],
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '采购单号', w: 170, filter: { type: 'text' } }, 
    { k: 'orderType', l: '类型', w: 90, filter: { type: 'select', options: [{ value: 'NORMAL', label: '常规采购' }, { value: 'URGENT', label: '紧急采购' }] } }, 
    { k: 'supplierName', l: '供应商', filter: { type: 'text' } }, 
    { k: 'warehouseName', l: '入库仓库', w: 120, filter: { type: 'text' } }, 
    { k: 'totalAmount', l: '总金额', w: 120, filter: { type: 'number' } }, 
    { k: 'finalAmount', l: '实付金额', w: 120, filter: { type: 'number' } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'SUBMITTED', label: '待审批' }, { value: 'APPROVED', label: '已审批' }, { value: 'RECEIVING', label: '收货中' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }, { value: 'REJECTED', label: '已驳回' }] } }, 
    { k: 'auditUserName', l: '审核人', w: 90, filter: { type: 'text' } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'orderType', label: '采购类型', type: 'select', required: true, value: 'NORMAL', group: '采购信息', options: [{ value: 'NORMAL', label: '常规采购' }, { value: 'URGENT', label: '紧急采购' }] },
    { key: 'supplierId', label: '供应商', required: true, picker: 'suppliers', group: '采购信息' },
    { key: 'warehouseId', label: '入库仓库', required: true, picker: 'warehouses', group: '采购信息' },
    { key: 'expectedDate', label: '期望到货日期', type: 'date', group: '采购信息' },
    { key: 'remark', label: '采购备注', type: 'textarea', group: '其它' },
    { key: 'lines', type: 'lines', label: '采购明细', required: true, group: '采购明细', cols: LINE_ORDER }
  ]
}

const purchaseReturns = {
  key: 'purchase-returns', title: '采退订单', api: '/api/purchase-orders', extraParams: { isRed: true }, apiCreate: '/api/purchase-orders', detailable: true,
  statusActions: [
    { label: '提交审批', when: ['DRAFT'], method: 'POST', path: '/submit', type: 'primary', confirm: '确认提交此采退订单进入审批？' },
    { label: '审批通过', when: ['SUBMITTED'], method: 'POST', path: '/approve', type: 'success', confirm: '确认审批通过此采退订单？' },
    { label: '驳回', when: ['SUBMITTED'], method: 'POST', path: '/reject', type: 'danger', confirm: '确认驳回此采退订单？' },
    { label: '取消', when: ['DRAFT', 'SUBMITTED'], method: 'POST', path: '/cancel', type: 'warning', confirm: '确认取消此采退订单？' }
  ],
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '采退单号', w: 170, filter: { type: 'text' } }, 
    { k: 'supplierName', l: '供应商', filter: { type: 'text' } }, 
    { k: 'warehouseName', l: '出库仓库', w: 120, filter: { type: 'text' } }, 
    { k: 'finalAmount', l: '金额', w: 120, filter: { type: 'number' } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'SUBMITTED', label: '待审批' }, { value: 'APPROVED', label: '已审批' }, { value: 'RECEIVING', label: '收货中' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }, { value: 'REJECTED', label: '已驳回' }] } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'isRed', label: '红字标记', type: 'boolean', value: true, readonly: true, group: '采退信息' },
    { key: 'orderType', label: '采退类型', type: 'select', required: true, value: 'RETURN', group: '采退信息', options: [{ value: 'RETURN', label: '采退' }] },
    { key: 'refPoId', label: '原采购单', required: true, picker: 'purchase-orders', group: '采退信息' },
    { key: 'supplierId', label: '供应商', required: true, picker: 'suppliers', group: '采退信息' },
    { key: 'warehouseId', label: '出库仓库', required: true, picker: 'warehouses', group: '采退信息' },
    { key: 'remark', label: '采退原因', type: 'textarea', required: true, group: '其它' },
    { key: 'lines', type: 'lines', label: '采退明细', required: true, group: '采退明细', cols: LINE_ORDER }
  ]
}

const inventory = {
  key: 'inventory', title: '库存查询', api: '/api/inventory', readonly: true,
  cols: [
    { k: 'productId', l: '产品ID', w: 80, filter: { type: 'number' } }, 
    { k: 'productCode', l: '产品编码', w: 130, filter: { type: 'text' } }, 
    { k: 'productName', l: '产品名称', filter: { type: 'text' } }, 
    { k: 'warehouseId', l: '仓库ID', w: 80, filter: { type: 'number' } }, 
    { k: 'warehouseName', l: '仓库', w: 120, filter: { type: 'text' } }, 
    { k: 'batchNo', l: '批次号', w: 120, filter: { type: 'text' } }, 
    { k: 'serialNo', l: '序列号', w: 130, filter: { type: 'text' } }, 
    { k: 'stockStatus', l: '库存状态', w: 90, filter: { type: 'select', options: getDictOptions('stock_status') } }, 
    { k: 'qty', l: '数量', w: 90, filter: { type: 'number' } }, 
    { k: 'expDate', l: '到期日', w: 110, filter: { type: 'date' } }, 
    { k: 'inSource', l: '入库来源', w: 110, filter: { type: 'text' } }
  ]
}

const salesOuts = {
  key: 'sales-outs', title: '销售出库', api: '/api/sales-outs', detailable: true, importable: false, exportable: true, noEdit: true, noCreate: true, noDelete: true, maxActions: 1,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '出库单号', w: 170, filter: { type: 'text' } },
    { k: 'dealerName', l: '经销商', w: 140, filter: { type: 'text' } },
    { k: 'warehouseName', l: '仓库', w: 110, filter: { type: 'text' } },
    { k: 'sourceOrderCode', l: '来源订单', w: 150, filter: { type: 'text' } },
    { k: 'status', l: '状态', w: 110, filter: { type: 'select', options: [
      { value: 'DRAFT', label: '草稿' },
      { value: 'APPROVED', label: '已审批' },
      { value: 'PARTIAL_SHIPPED', label: '部分发货' },
      { value: 'COMPLETED', label: '已完成' },
      { value: 'CANCELLED', label: '已取消' }
    ] } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  statusActions: {
    DRAFT: [{ key: 'open', label: '打开', path: '/sales-out-edit', type: 'primary', isRoute: true }],
    APPROVED: [{ key: 'open', label: '打开', path: '/sales-out-edit', type: 'primary', isRoute: true }],
    PARTIAL_SHIPPED: [{ key: 'open', label: '打开', path: '/sales-out-edit', type: 'primary', isRoute: true }],
    COMPLETED: [{ key: 'open', label: '查看', path: '/sales-out-edit', type: 'default', isRoute: true }],
    CANCELLED: [{ key: 'open', label: '查看', path: '/sales-out-edit', type: 'default', isRoute: true }]
  }
}
const receipts = {
  key: 'receipts', title: '收货入库', api: '/api/receipts', detailable: true, importable: false, exportable: true, noEdit: true, noCreate: true, noDelete: true, maxActions: 3,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '入库单号', w: 170, filter: { type: 'text' } },
    { k: 'warehouseName', l: '仓库', w: 110, filter: { type: 'text' } },
    { k: 'sourcePoCode', l: '来源采购单', w: 150, filter: { type: 'text' } },
    { k: 'status', l: '状态', w: 110, filter: { type: 'select', options: [
      { value: 'DRAFT', label: '草稿' },
      { value: 'APPROVED', label: '已审批' },
      { value: 'PARTIAL_RECEIVED', label: '部分入库' },
      { value: 'RECEIVED', label: '已入库' },
      { value: 'COMPLETED', label: '已完成' },
      { value: 'CANCELLED', label: '已取消' }
    ] } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  statusActions: {
    DRAFT: [
      { key: 'open', label: '打开', path: '/receipt-edit', type: 'primary', isRoute: true }
    ],
    APPROVED: [
      { key: 'open', label: '打开', path: '/receipt-edit', type: 'primary', isRoute: true }
    ],
    PARTIAL_RECEIVED: [
      { key: 'open', label: '打开', path: '/receipt-edit', type: 'primary', isRoute: true }
    ],
    RECEIVED: [
      { key: 'open', label: '查看', path: '/receipt-edit', type: 'default', isRoute: true }
    ],
    COMPLETED: [
      { key: 'open', label: '查看', path: '/receipt-edit', type: 'default', isRoute: true }
    ],
    CANCELLED: [
      { key: 'open', label: '查看', path: '/receipt-edit', type: 'default', isRoute: true }
    ]
  }
}

const stockMoves = {
  key: 'stock-moves', title: '库存移动', api: '/api/stock-moves', detailable: true, noEdit: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '调拨单号', w: 160, filter: { type: 'text' } }, 
    { k: 'moveType', l: '移动类型', w: 100, filter: { type: 'select', options: getDictOptions('move_type') } }, 
    { k: 'fromWarehouseName', l: '源仓库', w: 120, filter: { type: 'text' } }, 
    { k: 'fromWarehouseId', l: '源仓库ID', w: 100, filter: { type: 'number' } }, 
    { k: 'toWarehouseName', l: '目标仓库', w: 120, filter: { type: 'text' } }, 
    { k: 'toWarehouseId', l: '目标仓库ID', w: 100, filter: { type: 'number' } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'CONFIRMED', label: '已确认' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }] } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'fromWarehouseId', label: '源仓库', required: true, picker: 'warehouses', group: '移动信息' },
    { key: 'toWarehouseId', label: '目标仓库', required: true, picker: 'warehouses', group: '移动信息' },
    { key: 'stockStatus', label: '原库位库存状态', type: 'select', value: 'QUALIFIED', group: '移动信息', options: [{ value: 'QUALIFIED', label: '合格' }, { value: 'PENDING', label: '待检' }, { value: 'DEFECTIVE', label: '不合格' }] },
    { key: 'remark', label: '备注/原因', type: 'textarea', group: '其它' },
    { key: 'lines', type: 'lines', label: '移动明细', required: true, group: '移动明细', cols: LINE_STOCK }
  ]
}

const inventoryAdjustments = {
  key: 'inventory-adjustments', title: '库存调整', api: '/api/inventory-adjustments', detailable: true, noEdit: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '调整单号', w: 160, filter: { type: 'text' } }, 
    { k: 'category', l: '调整方向', w: 90, filter: { type: 'select', options: [{ value: 'IN', label: '盘盈(增加)' }, { value: 'OUT', label: '盘亏(扣减)' }] } }, 
    { k: 'type', l: '调整类型', w: 110, filter: { type: 'select', options: [{ value: 'STOCKTAKE', label: '盘点差异' }, { value: 'DAMAGE', label: '报损' }, { value: 'CORRECT', label: '数据修正' }, { value: 'OTHER', label: '其他' }] } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'CONFIRMED', label: '已确认' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }] } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'warehouseId', label: '仓库', required: true, picker: 'warehouses', group: '调整信息' },
    { key: 'category', label: '调整方向', type: 'select', required: true, value: 'IN', group: '调整信息', options: [{ value: 'IN', label: '盘盈(增加)' }, { value: 'OUT', label: '盘亏(扣减)' }] },
    { key: 'type', label: '调整类型', type: 'select', required: true, value: 'STOCKTAKE', group: '调整信息', options: [{ value: 'STOCKTAKE', label: '盘点差异' }, { value: 'DAMAGE', label: '报损' }, { value: 'CORRECT', label: '数据修正' }, { value: 'OTHER', label: '其他' }] },
    { key: 'stockStatus', label: '库存状态', type: 'select', value: 'QUALIFIED', group: '调整信息', options: [{ value: 'QUALIFIED', label: '合格' }, { value: 'PENDING', label: '待检' }, { value: 'DEFECTIVE', label: '不合格' }] },
    { key: 'remark', label: '原因说明', type: 'textarea', required: true, group: '其它' },
    { key: 'lines', type: 'lines', label: '调整明细', required: true, group: '调整明细', cols: LINE_STOCK }
  ]
}

const surgeryReports = {
  key: 'surgery-reports', title: '手术植入报台', api: '/api/surgery-reports', detailable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '报台单号', w: 160, filter: { type: 'text' } }, 
    { k: 'dealerName', l: '经销商', w: 120, filter: { type: 'text' } }, 
    { k: 'terminalName', l: '医院', w: 120, filter: { type: 'text' } }, 
    { k: 'surgeryDate', l: '手术日期', w: 110, filter: { type: 'date' } }, 
    { k: 'doctorName', l: '主刀医生', w: 90, filter: { type: 'text' } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'CONFIRMED', label: '已确认' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }] } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'dealerId', label: '经销商', required: true, picker: 'dealers', group: '手术信息' },
    { key: 'terminalId', label: '医院/终端(须已授权)', required: true, picker: 'hospitals', group: '手术信息' },
    { key: 'warehouseId', label: '出库仓库', required: true, picker: 'warehouses', group: '手术信息' },
    { key: 'surgeryDate', label: '手术日期', type: 'date', required: true, group: '手术信息' },
    { key: 'patientInfo', label: '患者信息', group: '手术详情', placeholder: '如：李某某，男，65岁' },
    { key: 'doctorName', label: '主刀医生', group: '手术详情' },
    { key: 'remark', label: '手术备注', type: 'textarea', group: '其它' },
    { key: 'lines', type: 'lines', label: '植入产品明细', required: true, group: '植入明细', cols: [
      { k: 'productId', l: '产品', type: 'picker', picker: 'products', format: 'productName' }, { k: 'qty', l: '数量', type: 'number' },
      { k: 'batchNo', l: '批次号' }, { k: 'serialNo', l: '序列号' }, { k: 'unitPrice', l: '单价', type: 'number' }
    ] }
  ]
}

const users = {
  key: 'users', title: '账号管理', api: '/api/users',
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'username', l: '账号', filter: { type: 'text' } },
    { k: 'name', l: '姓名', filter: { type: 'text' } },
    { k: 'userType', l: '类型', w: 80, filter: { type: 'select', options: [{ value: 'vendor', label: '厂商' }, { value: 'dealer', label: '经销商' }] } },
    { k: 'email', l: '邮箱', filter: { type: 'text' } },
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: [{ value: 'active', label: '启用' }, { value: 'inactive', label: '停用' }, { value: 'locked', label: '锁定' }] } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'username', label: '账号', required: true, group: '基本信息' },
    { key: 'name', label: '姓名', required: true, group: '基本信息' },
    { key: 'userType', label: '用户类型', type: 'select', required: true, value: 'vendor', group: '基本信息', options: [{ value: 'vendor', label: '厂商' }, { value: 'dealer', label: '经销商' }] },
    { key: 'password', label: '初始密码', type: 'password', group: '基本信息', placeholder: '留空则用默认密码' },
    { key: 'email', label: '邮箱', type: 'email', group: '联系信息' },
    { key: 'phone', label: '手机号', group: '联系信息' },
    { key: 'orgId', label: '所属组织', picker: 'org-units', group: '归属' },
    { key: 'dealerId', label: '所属经销商(dealer)', picker: 'dealers', group: '归属' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '状态', options: [{ value: 'active', label: '启用' }, { value: 'inactive', label: '停用' }, { value: 'locked', label: '锁定' }] }
  ]
}

const positions = {
  key: 'positions', title: '销售岗位', api: '/api/sales-positions',
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '岗位编码', w: 130, filter: { type: 'text' } },
    { k: 'name', l: '岗位名称', filter: { type: 'text' } },
    { k: 'orgName', l: '所属组织', w: 150, filter: { type: 'text' } },
    { k: 'level', l: '级别', w: 80, filter: { type: 'text' } },
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'code', label: '岗位编码', required: true, group: '基本信息' },
    { key: 'name', label: '岗位名称', required: true, group: '基本信息' },
    { key: 'orgId', label: '所属组织', picker: 'org-units', group: '基本信息' },
    { key: 'level', label: '岗位级别', group: '基本信息' },
    { key: 'description', label: '岗位描述', type: 'textarea', group: '其它' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '状态', options: S_ACTIVE }
  ]
}

const roles = {
  key: 'roles', title: '角色管理', api: '/api/roles',
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '编码', filter: { type: 'text' } }, 
    { k: 'name', l: '名称', filter: { type: 'text' } }, 
    { k: 'type', l: '类型', w: 90, filter: { type: 'select', options: [{ value: 'system', label: '系统角色' }, { value: 'custom', label: '自定义角色' }] } }, 
    { k: 'description', l: '描述', filter: { type: 'text' } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'code', label: '角色编码', required: true, group: '基本信息' },
    { key: 'name', label: '角色名称', required: true, group: '基本信息' },
    { key: 'type', label: '角色类型', type: 'select', value: 'custom', group: '基本信息', options: [{ value: 'system', label: '系统角色' }, { value: 'custom', label: '自定义角色' }] },
    { key: 'description', label: '描述', type: 'textarea', group: '其它' }
  ]
}

const reportBase = { readonly: true, searchable: false }
const reportSalesRanking = { ...reportBase, key: 'report-sales-ranking', title: '销售业绩排行', api: '/api/reports/sales-ranking',
  cols: [
    { k: 'dealerCode', l: '经销商编码', w: 120, filter: { type: 'text' } }, 
    { k: 'dealerName', l: '经销商名称', filter: { type: 'text' } }, 
    { k: 'level', l: '级别', w: 80 }, 
    { k: 'region', l: '区域', w: 90, filter: { type: 'text' } }, 
    { k: 'orderCount', l: '订单数', w: 80, filter: { type: 'number' } }, 
    { k: 'totalQty', l: '总数量', w: 90, filter: { type: 'number' } }, 
    { k: 'totalAmount', l: '销售总额', w: 130, filter: { type: 'number' } }, 
    { k: 'lastOrderAt', l: '最近下单', w: 130, filter: { type: 'date' } }
  ]
}
const reportProductTop10 = { ...reportBase, key: 'report-product-top10', title: '产品销售 TOP10', api: '/api/reports/product-top10',
  cols: [
    { k: 'productCode', l: '产品编码', w: 120, filter: { type: 'text' } }, 
    { k: 'productName', l: '产品名称', filter: { type: 'text' } }, 
    { k: 'spec', l: '规格', w: 110, filter: { type: 'text' } }, 
    { k: 'categoryName', l: '品类', w: 100, filter: { type: 'text' } }, 
    { k: 'orderCount', l: '订单数', w: 80, filter: { type: 'number' } }, 
    { k: 'totalQty', l: '销量', w: 90, filter: { type: 'number' } }, 
    { k: 'avgPrice', l: '均价', w: 100, filter: { type: 'number' } }, 
    { k: 'totalAmount', l: '销售额', w: 130, filter: { type: 'number' } }
  ]
}
const reportInventoryTurnover = { ...reportBase, key: 'report-inventory-turnover', title: '库存周转', api: '/api/reports/inventory-turnover',
  cols: [
    { k: 'productCode', l: '产品编码', w: 120, filter: { type: 'text' } }, 
    { k: 'productName', l: '产品名称', filter: { type: 'text' } }, 
    { k: 'currentStock', l: '当前库存', w: 100, filter: { type: 'number' } }, 
    { k: 'qualifiedStock', l: '合格库存', w: 100, filter: { type: 'number' } }, 
    { k: 'pendingStock', l: '待检库存', w: 100, filter: { type: 'number' } }, 
    { k: 'recentInQty', l: '近30入库', w: 100, filter: { type: 'number' } }, 
    { k: 'recentOutQty', l: '近30出库', w: 100, filter: { type: 'number' } }, 
    { k: 'turnoverDays', l: '周转天数', w: 90, filter: { type: 'number' } }
  ]
}
const reportSurgeryStats = { ...reportBase, key: 'report-surgery-stats', title: '手术报台统计', api: '/api/reports/surgery-stats',
  cols: [
    { k: 'hospitalCode', l: '医院编码', w: 120, filter: { type: 'text' } }, 
    { k: 'hospitalName', l: '医院', filter: { type: 'text' } }, 
    { k: 'level', l: '级别', w: 80 }, 
    { k: 'city', l: '城市', w: 90, filter: { type: 'text' } }, 
    { k: 'surgeryCount', l: '手术数', w: 80, filter: { type: 'number' } }, 
    { k: 'totalImplants', l: '植入件数', w: 100, filter: { type: 'number' } }, 
    { k: 'lastSurgeryAt', l: '最近手术', w: 130, filter: { type: 'date' } }
  ]
}
const reportReceivables = { ...reportBase, key: 'report-receivables', title: '应收账款', api: '/api/reports/receivables',
  cols: [
    { k: 'dealerCode', l: '经销商编码', w: 120, filter: { type: 'text' } },
    { k: 'dealerName', l: '经销商名称', filter: { type: 'text' } },
    { k: 'unpaidCount', l: '未结算单数', w: 100, filter: { type: 'number' } },
    { k: 'totalReceivable', l: '应收金额', w: 130, filter: { type: 'number' } },
    { k: 'age30', l: '0-30天', w: 110, filter: { type: 'number' } },
    { k: 'age60', l: '31-60天', w: 110, filter: { type: 'number' } },
    { k: 'age90', l: '61-90天', w: 110, filter: { type: 'number' } },
    { k: 'ageOver90', l: '>90天', w: 110, filter: { type: 'number' } }
  ]
}
const reportOrderTrace = { ...reportBase, key: 'report-order-trace', title: '订单追溯', api: '/api/reports/order-trace',
  cols: [
    { k: 'orderCode', l: '订单号', w: 170, filter: { type: 'text' } },
    { k: 'dealerName', l: '经销商', filter: { type: 'text' } },
    { k: 'status', l: '订单状态', w: 110, filter: { type: 'select', options: [
      { value: 'DRAFT', label: '草稿' },
      { value: 'PENDING_APPROVAL', label: '待审批' },
      { value: 'APPROVED', label: '已审批' },
      { value: 'SHIPPING', label: '发货中' },
      { value: 'COMPLETED', label: '已完成' },
      { value: 'CANCELLED', label: '已取消' }
    ] } },
    { k: 'totalAmount', l: '订单金额', w: 120, filter: { type: 'number' } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } },
    { k: 'approvedAt', l: '审批时间', w: 160, filter: { type: 'date' } },
    { k: 'shippedAt', l: '发货时间', w: 160, filter: { type: 'date' } },
    { k: 'completedAt', l: '完成时间', w: 160, filter: { type: 'date' } }
  ]
}

const productLines = {
  key: 'product-lines', title: '产品线管理', api: '/api/product-lines', detailable: true, importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '产品线编码', w: 130, filter: { type: 'text' } },
    { k: 'name', l: '产品线名称', filter: { type: 'text' } },
    { k: 'parentId', l: '父产品线ID', w: 100, filter: { type: 'number' } },
    { k: 'level', l: '层级', w: 80, filter: { type: 'number' } },
    { k: 'sortOrder', l: '排序', w: 80, filter: { type: 'number' } },
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'code', label: '产品线编码', required: true, group: '基本信息' },
    { key: 'name', label: '产品线名称', required: true, group: '基本信息' },
    { key: 'parentId', label: '父产品线', picker: 'product-lines', group: '基本信息' },
    { key: 'level', label: '层级', type: 'number', required: true, value: 1, group: '基本信息' },
    { key: 'description', label: '描述', type: 'textarea', group: '基本信息' },
    { key: 'sortOrder', label: '排序', type: 'number', value: 0, group: '基本信息' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '状态', options: S_ACTIVE }
  ]
}

const productPackageLevels = {
  key: 'product-package-levels', title: '产品包装层级', api: '/api/product-package-levels', detailable: true, importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '包装编码', w: 130, filter: { type: 'text' } },
    { k: 'name', l: '包装名称', filter: { type: 'text' } },
    { k: 'productId', l: '产品ID', w: 100, filter: { type: 'number' } },
    { k: 'parentId', l: '父包装ID', w: 100, filter: { type: 'number' } },
    { k: 'level', l: '层级', w: 80, filter: { type: 'number' } },
    { k: 'quantity', l: '数量', w: 80, filter: { type: 'number' } },
    { k: 'uom', l: '单位', w: 80, filter: { type: 'text' } },
    { k: 'gtin', l: 'GTIN', w: 130, filter: { type: 'text' } },
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'productId', label: '产品', required: true, picker: 'products', group: '基本信息' },
    { key: 'code', label: '包装编码', required: true, group: '基本信息' },
    { key: 'name', label: '包装名称', required: true, group: '基本信息' },
    { key: 'parentId', label: '父包装', picker: 'product-package-levels', group: '基本信息' },
    { key: 'level', label: '层级', type: 'number', required: true, value: 1, group: '基本信息' },
    { key: 'quantity', label: '数量', type: 'number', required: true, value: 1, group: '基本信息' },
    { key: 'uom', label: '单位', group: '基本信息' },
    { key: 'barcodeFormat', label: '条码格式', group: '条码信息' },
    { key: 'gtin', label: 'GTIN', group: '条码信息' },
    { key: 'snRule', label: '序列号规则', group: '条码信息' },
    { key: 'description', label: '描述', type: 'textarea', group: '基本信息' },
    { key: 'sortOrder', label: '排序', type: 'number', value: 0, group: '基本信息' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '状态', options: S_ACTIVE }
  ]
}

const productBundles = {
  key: 'product-bundles', title: '产品组合', api: '/api/product-bundles', detailable: true, importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '组合编码', w: 130, filter: { type: 'text' } },
    { k: 'name', l: '组合名称', filter: { type: 'text' } },
    { k: 'productId', l: '产品ID', w: 100, filter: { type: 'number' } },
    { k: 'pricingType', l: '定价类型', w: 100, filter: { type: 'select', options: [
      { value: 'STANDARD', label: '标准定价' },
      { value: 'DISCOUNT', label: '折扣定价' },
      { value: 'FIXED', label: '固定价格' }
    ] } },
    { k: 'bundlePrice', l: '组合价格', w: 120, filter: { type: 'number' } },
    { k: 'allowSplit', l: '允许拆分', w: 90, filter: { type: 'select', options: [
      { value: true, label: '是' },
      { value: false, label: '否' }
    ] } },
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } },
    { k: 'validFrom', l: '生效日期', w: 110, filter: { type: 'date' } },
    { k: 'validTo', l: '失效日期', w: 110, filter: { type: 'date' } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'date' } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'date' } }
  ],
  form: [
    { key: 'productId', label: '产品', required: true, picker: 'products', group: '基本信息' },
    { key: 'code', label: '组合编码', required: true, group: '基本信息' },
    { key: 'name', label: '组合名称', required: true, group: '基本信息' },
    { key: 'description', label: '描述', type: 'textarea', group: '基本信息' },
    { key: 'pricingType', label: '定价类型', type: 'select', required: true, value: 'STANDARD', group: '定价信息', options: [
      { value: 'STANDARD', label: '标准定价' },
      { value: 'DISCOUNT', label: '折扣定价' },
      { value: 'FIXED', label: '固定价格' }
    ] },
    { key: 'bundlePrice', label: '组合价格', type: 'number', group: '定价信息' },
    { key: 'allowSplit', label: '允许拆分', type: 'boolean', value: false, group: '规则信息' },
    { key: 'splitRule', label: '拆分规则', type: 'textarea', group: '规则信息' },
    { key: 'versionNote', label: '版本说明', type: 'textarea', group: '版本信息' },
    { key: 'validFrom', label: '生效日期', type: 'date', group: '有效期' },
    { key: 'validTo', label: '失效日期', type: 'date', group: '有效期' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '状态', options: S_ACTIVE }
  ]
}

export const MODULE_CONFIGS = {
  products, categories, dealers, hospitals, warehouses, regions, materials, suppliers,
  'product-lines': productLines, 'product-package-levels': productPackageLevels, 'product-bundles': productBundles,
  'product-prices': productPrices,
  'contract-apps': contractApps, contracts, authorizations, promotions,
  orders, 'sales-returns': salesReturns, 'purchase-orders': purchaseOrders, 'purchase-returns': purchaseReturns,
  inventory, 'sales-outs': salesOuts, receipts, 'stock-moves': stockMoves, 'inventory-adjustments': inventoryAdjustments,
  'surgery-reports': surgeryReports, users, positions, roles,
  'report-sales-ranking': reportSalesRanking, 'report-product-top10': reportProductTop10,
  'report-inventory-turnover': reportInventoryTurnover, 'report-surgery-stats': reportSurgeryStats,
  'report-receivables': reportReceivables, 'report-order-trace': reportOrderTrace
}
