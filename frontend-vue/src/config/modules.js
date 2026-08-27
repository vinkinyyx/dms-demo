const S_ACTIVE = [{ value: 'active', label: '启用' }, { value: 'inactive', label: '停用' }]
const S_ACTIVE_BLOCK = [...S_ACTIVE, { value: 'blocked', label: '冻结' }]
const UNITS = [{ value: '个', label: '个' }, { value: '盒', label: '盒' }, { value: '箱', label: '箱' }, { value: '支', label: '支' }, { value: '瓶', label: '瓶' }, { value: '包', label: '包' }, { value: '套', label: '套' }]
const CURRENCIES = [{ value: 'CNY', label: '人民币(CNY)' }, { value: 'USD', label: '美元(USD)' }, { value: 'EUR', label: '欧元(EUR)' }]

// 获取数据字典选项
import { getDictOptions } from '@/utils/dict'
import { ElMessage, ElMessageBox } from 'element-plus'
import { actionResource } from '@/api/crud'

const products = {
  key: 'products', title: '产品管理', api: '/api/products', detailable: true, detailPath: '/products', businessType: 'product', createPermission: 'product:create', importable: true, exportable: true, batchDelete: false,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '产品编码', w: 130, filter: { type: 'text' } }, 
    { k: 'nameCn', l: '中文名称', filter: { type: 'text' } },
    { k: 'productType', l: '产品类型', w: 100, filter: { type: 'select', options: getDictOptions('product_type') } },
    { k: 'categoryName', l: '产品分类', w: 120, filter: { type: 'select', remote: 'categories' } },
    { k: 'spec', l: '规格型号', w: 120, filter: { type: 'text' } }, 
    { k: 'unit', l: '单位', w: 70, filter: { type: 'select', options: UNITS } }, 
    { k: 'udiRequired', l: 'UDI追溯', w: 80, filter: { type: 'select', options: [{ value: true, label: '是' }, { value: false, label: '否' }] } },
    { k: 'isSerialManaged', l: '序列号管理', w: 90, filter: { type: 'select', options: [{ value: true, label: '是' }, { value: false, label: '否' }] } },
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
  ],
  form: [
    { key: 'code', label: '产品编码', required: true, group: '基本信息', placeholder: '如 PROD-XXXXX' },
    { key: 'nameCn', label: '中文名称', required: true, group: '基本信息' },
    { key: 'nameEn', label: '英文名称', group: '基本信息' },
    { key: 'productType', label: '产品类型', type: 'select', required: true, group: '基本信息', options: getDictOptions('product_type') },
    { key: 'categoryId', label: '产品分类', required: true, group: '基本信息', picker: 'categories' },
    { key: 'productLineId', label: '产品层次', group: '基本信息', picker: 'product-lines' },
    { key: 'spec', label: '规格型号', group: '规格信息' },
    { key: 'unit', label: '单位', type: 'select', group: '规格信息', value: '个', options: UNITS },
    { key: 'udiRequired', label: '需要UDI追溯', type: 'boolean', group: '医疗器械', value: true },
    { key: 'isSerialManaged', label: '序列号管理', type: 'boolean', group: '医疗器械', value: false },
    { key: 'warnMonths', label: '临期预警(月)', type: 'number', group: '医疗器械', value: 3 },
    { key: 'safetyQty', label: '安全库存', type: 'number', group: '库存参数', value: 10 },
    { key: 'minOrderQty', label: '最小订购量', type: 'number', group: '库存参数', value: 1 },
    { key: 'status', label: '状态', type: 'select', group: '状态', value: 'active', options: S_ACTIVE }
  ]
}

const categories = {
  key: 'categories', title: '产品分类', api: '/api/product-categories', detailable: true, detailPath: '/categories', businessType: 'productCategory', createPermission: 'product_category:create', importable: true, exportable: true, batchDelete: false,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '分类编码', w: 140, filter: { type: 'text' } }, 
    { k: 'name', l: '分类名称', filter: { type: 'text' } }, 
    { k: 'sortOrder', l: '排序', w: 80, filter: { type: 'number' } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
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
  key: 'dealers', title: '经销商管理', api: '/api/dealers', detailable: true, detailPath: '/dealers', businessType: 'dealer', createPermission: 'dealer:create', importable: true, exportable: true, batchDelete: false,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '经销商编码', w: 130, filter: { type: 'text' } }, 
    { k: 'name', l: '经销商名称', filter: { type: 'text' } }, 
    { k: 'level', l: '级别', w: 70, filter: { type: 'select', options: [{ value: 'VIP', label: 'VIP' }, { value: 'LEVEL_1', label: '一级' }, { value: 'LEVEL_2', label: '二级' }, { value: 'LEVEL_3', label: '三级' }, { value: 'NORMAL', label: '普通' }] } }, 
    { k: 'legalPerson', l: '法人', w: 110, filter: { type: 'text' } }, 
    { k: 'contactPhone', l: '联系电话', w: 120, filter: { type: 'text' } }, 
    { k: 'gspStatus', l: 'GSP资质', w: 90, filter: { type: 'select', options: [{ value: 'active', label: '有效' }, { value: 'expired', label: '已过期' }, { value: 'none', label: '无' }] } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE_BLOCK } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
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
    { key: 'status', label: '状态', type: 'select', group: '状态', value: 'active', options: S_ACTIVE_BLOCK },
    { key: 'consignmentEnabled', label: '开启寄售库存', type: 'boolean', group: '资信与寄售', value: false },
    { key: 'consignmentLimit', label: '寄售额度', type: 'number', group: '资信与寄售', value: 0 },
    { key: 'creditLimit', label: '信用额度', type: 'number', group: '资信与寄售', value: 0 },
    { key: 'paymentDays', label: '账期(天)', type: 'number', group: '资信与寄售', value: 0 },
    { key: 'settlementMethod', label: '结算方式', type: 'select', group: '资信与寄售', options: [
        { value: 'MONTHLY', label: '月结' }, { value: 'COD', label: '货到付款' }, { value: 'ADVANCE', label: '预付款' }, { value: 'CONSIGNMENT', label: '寄售结算' } ] },
    { key: 'creditGrade', label: '信用等级', type: 'select', group: '资信与寄售', options: [
        { value: 'A', label: 'A' }, { value: 'B', label: 'B' }, { value: 'C', label: 'C' }, { value: 'D', label: 'D' } ] }
  ]
}

const hospitals = {
  key: 'hospitals', title: '医院管理', api: '/api/hospitals', detailable: true, detailPath: '/hospitals', businessType: 'hospital', createPermission: 'hospital:create', importable: true, exportable: true, batchDelete: false,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '医院编码', w: 130, filter: { type: 'text' } }, 
    { k: 'name', l: '医院名称', filter: { type: 'text' } }, 
    { k: 'level', l: '等级', w: 90, filter: { type: 'select', options: [{ value: '三甲', label: '三级甲等' }, { value: '三乙', label: '三级乙等' }, { value: '二甲', label: '二级甲等' }, { value: '二乙', label: '二级乙等' }, { value: '一级', label: '一级' }, { value: '未定', label: '未定级' }] } }, 
    { k: 'phone', l: '联系电话', w: 120, filter: { type: 'text' } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
  ],
  form: [
    { key: 'code', label: '医院编码', required: true, group: '基本信息' },
    { key: 'name', label: '医院名称', required: true, group: '基本信息' },
    { key: 'level', label: '医院等级', type: 'select', group: '基本信息', options: [{ value: '三甲', label: '三级甲等' }, { value: '三乙', label: '三级乙等' }, { value: '二甲', label: '二级甲等' }, { value: '二乙', label: '二级乙等' }, { value: '一级', label: '一级' }, { value: '未定', label: '未定级' }] },
    { key: 'regionId', label: '所属区域', group: '基本信息', picker: 'regions' },
    { key: 'contact', label: '联系人', group: '联系信息' },
    { key: 'phone', label: '联系电话', group: '联系信息' },
    { key: 'address', label: '地址', type: 'textarea', group: '联系信息' },
    { key: 'status', label: '状态', type: 'select', group: '状态', value: 'active', options: S_ACTIVE }
  ]
}

const warehouses = {
  key: 'warehouses', title: '仓库管理', api: '/api/warehouses', detailable: true, detailPath: '/warehouses', businessType: 'warehouse', createPermission: 'warehouse:create', importable: true, exportable: true, batchDelete: false,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '仓库编码', w: 130, filter: { type: 'text' } }, 
    { k: 'name', l: '仓库名称', filter: { type: 'text' } }, 
    { k: 'type', l: '类型', w: 100, filter: { type: 'select', options: getDictOptions('warehouse_type') } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
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
  key: 'regions', title: '区域管理', api: '/api/regions', detailable: true, detailPath: '/regions', businessType: 'region', createPermission: 'region:create', importable: true, exportable: true, batchDelete: false,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '区域编码', w: 140, filter: { type: 'text' } }, 
    { k: 'name', l: '区域名称', filter: { type: 'text' } }, 
    { k: 'level', l: '级别', w: 80, filter: { type: 'number', range: true } }, 
    { k: 'sortOrder', l: '排序', w: 80, filter: { type: 'number' } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
  ],
  form: [
    { key: 'code', label: '区域编码', required: true, group: '基本信息' },
    { key: 'name', label: '区域名称', required: true, group: '基本信息' },
    { key: 'level', label: '级别', type: 'number', value: 1, group: '基本信息' },
    { key: 'parentId', label: '父区域', picker: 'regions', group: '基本信息' },
    { key: 'sortOrder', label: '排序', type: 'number', value: 1, group: '基本信息' }
  ]
}

const suppliers = {
  key: 'suppliers', title: '供应商管理', api: '/api/suppliers', detailable: true, detailPath: '/suppliers', businessType: 'supplier', createPermission: 'supplier:create', importable: true, exportable: true, batchDelete: false,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '供应商编码', w: 130, filter: { type: 'text' } }, 
    { k: 'name', l: '供应商名称', filter: { type: 'text' } }, 
    { k: 'level', l: '供应商等级', w: 90, filter: { type: 'select', options: getDictOptions('supplier_level') } }, 
    { k: 'contactPerson', l: '联系人', w: 100, filter: { type: 'text' } }, 
    { k: 'phone', l: '联系电话', w: 120, filter: { type: 'text' } }, 
    { k: 'taxNo', l: '税号', w: 160, filter: { type: 'text' } }, 
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE_BLOCK } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
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
  key: 'product-prices', title: '产品价格', api: '/api/product-prices', detailable: true, detailPath: '/product-prices', businessType: 'product_price', createPermission: 'product_price:create', noDelete: true, noEdit: true, importable: false, exportable: true,
  rowActions: [
    { key: 'activate', label: '生效', type: 'success', when: ['inactive'] },
    { key: 'deactivate', label: '失效', type: 'warning', when: ['active'] }
  ],
  cols: [
    { k: 'id', l: '编号', w: 60 },
    { k: 'productCode', l: 'SKU编码', w: 130, filter: { type: 'text' } },
    { k: 'productName', l: 'SKU名称', minWidth: 160, showOverflowTooltip: true },
    { k: 'priceTypeText', l: '价格类型', w: 100, filter: { type: 'select', options: [{ value: 'SALE', label: '销售价' }, { value: 'PURCHASE', label: '采购价' }] } },
    { k: 'priceContextText', l: '价格用途', w: 110 },
    { k: 'partnerName', l: '经销商/供应商', w: 180, filter: { type: 'resource', resource: 'dealers', paramKey: 'partnerId' } },
    { k: 'currency', l: '币种', w: 80 },
    { k: 'inclPrice', l: '含税价', w: 110 },
    { k: 'exclPrice', l: '不含税价', w: 120 },
    { k: 'taxRate', l: '税率', w: 80 },
    { k: 'validFrom', l: '生效开始', w: 120, filter: { type: 'date', range: true } },
    { k: 'validTo', l: '生效结束', w: 120, filter: { type: 'date', range: true } },
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } }
  ],
  form: [
    { key: 'priceType', label: '价格类型', type: 'select', required: true, value: 'SALE', group: '价格对象', options: [{ value: 'SALE', label: '销售价' }, { value: 'PURCHASE', label: '采购价' }] },
    { key: 'productId', label: 'SKU', required: true, type: 'product-picker', group: '价格对象' },
    { key: 'partnerId', label: '经销商', required: true, picker: 'dealers', group: '价格对象', showWhen: ['priceType', 'SALE'] },
    { key: 'componentPrices', label: 'BOM子件销售价', type: 'component-prices', group: '价格信息', full: true },
    { key: 'currency', label: '币种', type: 'select', value: 'CNY', group: '价格对象', options: CURRENCIES },
    { key: 'inclPrice', label: '含税价', type: 'number', precision: 4, required: true, group: '价格信息', calc: { op: 'divide', from: ['inclPrice','taxRate'], target: 'exclPrice' } },
    { key: 'taxRate', label: '税率', type: 'number', precision: 4, value: 0.13, min: 0, max: 1, group: '价格信息', calc: { op: 'divide', from: ['inclPrice','taxRate'], target: 'exclPrice' } },
    { key: 'exclPrice', label: '不含税价', type: 'number', precision: 4, group: '价格信息', readonly: true },
    { key: 'validFrom', label: '生效开始', type: 'date', group: '有效期' },
    { key: 'validTo', label: '生效结束', type: 'date', group: '有效期' },
    { key: 'status', label: '状态', type: 'select', value: 'active', options: S_ACTIVE, group: '状态' }
  ]
}
const authorizations = {
  key: 'authorizations', title: '授权管理', api: '/api/authorizations', detailable: true, detailPath: '/authorizations', businessType: 'authorization', createPermission: 'authorization:create', exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '授权编号', w: 160, filter: { type: 'text' } }, 
    { k: 'dealerName', l: '经销商', filter: { type: 'text' } }, 
    { k: 'validFrom', l: '生效', w: 110, filter: { type: 'date', range: true } }, 
    { k: 'validTo', l: '截止', w: 110, filter: { type: 'date', range: true } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'active', label: '启用' }, { value: 'suspended', label: '挂起' }, { value: 'expired', label: '已过期' }] } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
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
  noDelete: true, key: 'promotions', title: '促销规则', api: '/api/promotions', detailable: true, detailPath: '/promotions', businessType: 'promotion', exportable: true,
  rowActions: [
    { key: 'activate', label: '启用', type: 'success', when: ['draft', 'inactive'] },
    { key: 'deactivate', label: '停用', type: 'warning', when: ['active'] }
  ],
  rowActionHandlers: {
    activate(row) { ElMessageBox.confirm('确认启用该促销规则？', '提示', { type: 'warning' }).then(async () => { await actionResource('/api/promotions', row.id, '/activate', 'POST'); ElMessage.success('已启用'); location.reload() }).catch(() => {}) },
    deactivate(row) { ElMessageBox.confirm('确认停用该促销规则？', '提示', { type: 'warning' }).then(async () => { await actionResource('/api/promotions', row.id, '/deactivate', 'POST'); ElMessage.success('已停用'); location.reload() }).catch(() => {}) }
  },
  cols: [
    { k: 'id', l: '编号', w: 60 },
    { k: 'code', l: '规则编码', w: 150, filter: { type: 'text' } },
    { k: 'name', l: '规则名称', minWidth: 160, filter: { type: 'text' } },
    { k: 'promoType', l: '促销模式', w: 120, filter: { type: 'select', options: [{ value: 'GIFT', label: '满A赠B' }, { value: 'FULL_REDUCTION', label: '满A减钱' }] } },
    { k: 'priority', l: '优先级', w: 90 },
    { k: 'validFrom', l: '开始时间', w: 170 },
    { k: 'validTo', l: '结束时间', w: 170 },
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'draft', label: '草稿' }, { value: 'active', label: '启用' }, { value: 'inactive', label: '停用' }] } }
  ],
  form: [
    { key: 'code', label: '规则编码', required: true, group: '基本信息' },
    { key: 'name', label: '规则名称', required: true, group: '基本信息' },
    { key: 'promoType', label: '促销模式', type: 'select', required: true, value: 'GIFT', group: '基本信息', options: [{ value: 'GIFT', label: '满A赠B' }, { value: 'FULL_REDUCTION', label: '满A减钱' }] },
    { key: 'priority', label: '优先级', type: 'number', value: 50, group: '基本信息' },
    { key: 'validFrom', label: '开始时间', type: 'datetime', group: '有效期' },
    { key: 'validTo', label: '结束时间', type: 'datetime', group: '有效期' },
    { key: 'description', label: '说明', type: 'textarea', group: '其他' },
    { key: 'status', label: '状态', type: 'select', value: 'draft', group: '其他', options: [{ value: 'draft', label: '草稿' }, { value: 'active', label: '启用' }, { value: 'inactive', label: '停用' }] },
    { key: 'rules', type: 'lines', label: '规则明细', required: true, group: '规则明细', cols: [
      { k: 'targetType', l: '命中对象类型', type: 'select', required: true, value: 'SKU', options: [{ value: 'SKU', label: 'SKU' }, { value: 'LINE', label: '产品层次' }] },
      { k: 'targetProductId', l: '命中SKU', type: 'picker', picker: 'products', displayKey: 'targetProductName', required: true, showIf: (ctx) => ctx.targetType === 'SKU' },
      { k: 'targetProductLineId', l: '命中产品层次', type: 'picker', picker: 'product-lines', displayKey: 'targetProductLineName', required: true, showIf: (ctx) => ctx.targetType === 'LINE' },
      { k: 'thresholdQty', l: '门槛数量A', type: 'number', required: true },
      { k: 'giftProductId', l: '赠品SKU', type: 'picker', picker: 'products', displayKey: 'giftProductName', required: true, showIf: (ctx) => ctx.promoType === 'GIFT' },
      { k: 'giftQty', l: '赠品数量', type: 'number', required: true, showIf: (ctx) => ctx.promoType === 'GIFT' },
      { k: 'cycle', l: '周期', type: 'select', value: 'ONCE', showIf: (ctx) => ctx.promoType === 'GIFT' || ctx.promoType === 'FULL_REDUCTION', options: [{ value: 'ONCE', label: '仅一次' }, { value: 'EVERY_N', label: '每满N循环' }] },
      { k: 'everyN', l: '每满N数量', type: 'number', required: true, showIf: (ctx) => (ctx.promoType === 'GIFT' || ctx.promoType === 'FULL_REDUCTION') && ctx.cycle === 'EVERY_N' },
      { k: 'reduceAmount', l: '减免金额', type: 'number', required: true, showIf: (ctx) => ctx.promoType === 'FULL_REDUCTION' }
    ] }
  ]
}
const orders = {
  key: 'orders', title: '销售订单', api: '/api/sales-orders', detailable: true, detailPath: '/orders', editPath: '/order-create/sales', noDelete: false, createPath: '/order-create/sales', createPermission: 'sales_order:create', keywordFields: ['销售订单号', '经销商'], maxActions: 3, pageSize: 30, editableWhen: ['DRAFT', 'REJECTED'], deletableWhen: ['DRAFT', 'REJECTED'],
  rowActions: [
    { key: 'cancel', label: '取消', type: 'warning', when: ['APPROVED'], method: 'POST', path: '/cancel', confirm: '确认取消此订单？' },
    { key: 'simulateShip', label: '生成出库单', type: 'primary', when: ['APPROVED'], method: 'POST', path: '/simulate-ship', confirm: '确认根据此订单生成销售出库单？' }
  ],
  rowButtonPermissions: { cancel: row => Number(row.shippedQty || 0) === 0 },
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '销售订单号', w: 170, filter: { type: 'text' } },
    { k: 'dealerName', l: '经销商', w: 200, filter: { type: 'resource', resource: 'dealers', paramKey: 'dealerId' } },
    { k: 'finalAmount', l: '最终金额', w: 120, filter: { type: 'number', range: true } },
    { k: 'status', l: '状态', w: 110, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'PENDING_APPROVAL', label: '审批中' }, { value: 'APPROVED', label: '已审批' }, { value: 'SHIPPING', label: '发货中' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }, { value: 'REJECTED', label: '已驳回' }] } },
    { k: 'createdAt', l: '创建时间', w: 170, filter: { type: 'datetime', range: true } },
    { k: 'updatedAt', l: '更新时间', w: 170, filter: { type: 'datetime', range: true } }
  ]
}

const salesReturns = {
  key: 'sales-returns', title: '销退订单', api: '/api/rma/orders/unified', detailable: true, noDelete: true, noEdit: true, maxActions: 2, pageSize: 30, createPath: '/sales-return-edit', createPermission: 'sales_return:create', keywordFields: ['销退单号', '经销商'], viewPath: '/sales-return-edit', readonlyQuery: true, exportable: false,
  editableWhen: ['DRAFT', 'REJECTED'],
  deletableWhen: ['DRAFT', 'REJECTED', 'CANCELLED'],
  statusActions: [
    { label: '提交审批', when: ['DRAFT', 'REJECTED'], method: 'POST', path: '/submit', type: 'warning', confirm: '确认提交此销退订单进入审批？' },
    { label: '审批通过', when: ['PENDING_APPROVAL'], method: 'POST', path: '/approve', type: 'success', confirm: '确认审批通过此销退订单？（将自动生成销退入库草稿）' },
    { label: '驳回', when: ['PENDING_APPROVAL'], method: 'POST', path: '/reject', type: 'danger', confirm: '确认驳回此销退订单？' },
    { label: '取消', when: ['DRAFT', 'APPROVED'], method: 'POST', path: '/cancel', type: 'warning', confirm: '确认取消此销退订单？' }
  ],
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '销退单号', w: 170, filter: { type: 'text' } },
    { k: 'dealerName', l: '经销商', w: 200, filter: { type: 'resource', resource: 'dealers', paramKey: 'dealerId' } },
    { k: 'warehouseName', l: '收货仓库', w: 120, filter: { type: 'resource', resource: 'warehouses', paramKey: 'warehouseId' } },
    { k: 'reasonCode', l: '退货原因', w: 120, filter: { type: 'select', options: [
      { value: 'NORMAL', label: '常规退货' },
      { value: 'PRE_OP_CONTAMINATION', label: '术前污染' },
      { value: 'QUALITY_ISSUE', label: '质量问题' },
      { value: 'NEAR_EXPIRY', label: '近效期退货' },
      { value: 'EXPIRED', label: '过期退货' },
      { value: 'OVER_SHIP', label: '多发/错发' },
      { value: 'CUSTOMER_RETURN', label: '客户原因' },
      { value: 'DAMAGED', label: '运输破损' },
      { value: 'OTHER', label: '其他' }
    ] } },
    { k: 'finalAmount', l: '金额', w: 110, filter: { type: 'number', range: true } },
    { k: 'status', l: '状态', w: 100, tag: (r) => {
      const map = { DRAFT: 'info', PENDING_APPROVAL: 'warning', APPROVED: 'primary', RECEIVING: 'primary', COMPLETED: 'success', CANCELLED: 'info', REJECTED: 'danger' }
      return { type: map[r.status] || 'info' }
    }, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'PENDING_APPROVAL', label: '审批中' }, { value: 'APPROVED', label: '已审批' }, { value: 'RECEIVING', label: '收货中' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }, { value: 'REJECTED', label: '已驳回' }] } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
  ]
}
const PURCHASE_LINE_COLS = [
  { k: 'productId', l: '产品SKU', type: 'picker', picker: 'products', displayKey: 'productName', required: true },
  { k: 'qty', l: '数量', type: 'number', required: true, precision: 4 },
  { k: 'remark', l: '备注' }
]

const purchaseOrders = {
  key: 'purchase-orders', title: '采购订单', api: '/api/purchase-orders', detailable: true, detailPath: '/purchase-orders', businessType: 'purchaseOrder', createPermission: 'purchase_order:create', keywordFields: ['采购单号', '供应商'], noDelete: true, editableWhen: ['DRAFT'], maxActions: 2, pageSize: 30,
  importable: true, exportable: true,
  statusActions: [
    { label: '提交审批', when: ['DRAFT'], method: 'POST', path: '/submit', type: 'primary', confirm: '确认提交此采购订单进入审批？' },
    { label: '审批通过', when: ['PENDING_APPROVAL'], method: 'POST', path: '/approve', type: 'success', confirm: '确认审批通过此采购订单？（将自动生成收货入库草稿）' },
    { label: '驳回', when: ['PENDING_APPROVAL'], method: 'POST', path: '/reject', type: 'danger', confirm: '确认驳回此采购订单？' },
    { label: '取消', when: ['DRAFT', 'APPROVED'], method: 'POST', path: '/cancel', type: 'warning', confirm: '确认取消此采购订单？' }
  ],
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '采购单号', w: 170, filter: { type: 'text' } }, 
    { k: 'orderType', l: '类型', w: 90, filter: { type: 'select', options: [{ value: 'NORMAL', label: '常规采购' }, { value: 'URGENT', label: '紧急采购' }] } }, 
    { k: 'supplierName', l: '供应商', filter: { type: 'resource', resource: 'suppliers', paramKey: 'supplierId' } }, 
    { k: 'warehouseName', l: '入库仓库', w: 120, filter: { type: 'resource', resource: 'warehouses', paramKey: 'warehouseId' } }, 
    { k: 'totalAmount', l: '总金额', w: 120, filter: { type: 'number', range: true } }, 
    { k: 'finalAmount', l: '实付金额', w: 120, filter: { type: 'number', range: true } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'PENDING_APPROVAL', label: '审批中' }, { value: 'APPROVED', label: '已审批' }, { value: 'RECEIVING', label: '收货中' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }, { value: 'REJECTED', label: '已驳回' }] } }, 
    { k: 'auditUserName', l: '审核人', w: 90, filter: { type: 'text' } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
  ],
  form: [
    { key: 'orderType', label: '采购类型', type: 'select', required: true, value: 'NORMAL', group: '采购信息', options: [{ value: 'NORMAL', label: '常规采购' }, { value: 'URGENT', label: '紧急采购' }] },
    { key: 'supplierId', label: '供应商', required: true, picker: 'suppliers', group: '采购信息' },
    { key: 'warehouseId', label: '入库仓库', required: true, picker: 'warehouses', group: '采购信息' },
    { key: 'expectedDate', label: '期望到货日期', type: 'date', group: '采购信息' },
    { key: 'remark', label: '采购备注', type: 'textarea', group: '其它' },
    { key: 'lines', type: 'lines', label: '采购明细', required: true, group: '采购明细', cols: PURCHASE_LINE_COLS }
  ]
}

const purchaseReturns = {
  key: 'purchase-returns', title: '采退订单', api: '/api/purchase-returns', detailable: true, noDelete: true, maxActions: 2, pageSize: 30, createPath: '/purchase-return-edit/new', createPermission: 'purchase_return:create', detailPath: '/purchase-return-edit',
  statusActions: [
    { label: '提交审批', when: ['DRAFT'], method: 'POST', path: '/submit', type: 'warning', confirm: '确认提交此采退订单进入审批？' },
    { label: '审批通过', when: ['PENDING_APPROVAL'], method: 'POST', path: '/approve', type: 'success', confirm: '确认审批通过此采退订单？（将自动生成采退出库草稿）' },
    { label: '驳回', when: ['PENDING_APPROVAL'], method: 'POST', path: '/reject', type: 'danger', confirm: '确认驳回此采退订单？' },
    { label: '取消', when: ['DRAFT', 'APPROVED'], method: 'POST', path: '/cancel', type: 'warning', confirm: '确认取消此采退订单？' }
  ],
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '采退单号', w: 170, filter: { type: 'text' } },
    { k: 'supplierName', l: '供应商', filter: { type: 'resource', resource: 'suppliers', paramKey: 'supplierId' } },
    { k: 'warehouseName', l: '出库仓库', w: 120, filter: { type: 'resource', resource: 'warehouses', paramKey: 'warehouseId' } },
    { k: 'finalAmount', l: '金额', w: 110, filter: { type: 'number', range: true } },
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'PENDING_APPROVAL', label: '审批中' }, { value: 'APPROVED', label: '已审批' }, { value: 'SHIPPING', label: '发货中' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }, { value: 'REJECTED', label: '已驳回' }] } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }
  ]
}
const inventory = {
  key: 'inventory', title: '库存查询', api: '/api/inventory', readonly: true,
  cols: [
    { k: 'productId', l: '物料', w: 110, filter: { type: 'resource', resource: 'products', paramKey: 'productId' } }, 
    { k: 'productCode', l: '产品编码', w: 130, filter: { type: 'text' } }, 
    { k: 'productName', l: '产品名称', filter: { type: 'text' } }, 
    { k: 'warehouseId', l: '仓库', w: 120, filter: { type: 'resource', resource: 'warehouses', paramKey: 'warehouseId' } }, 
    { k: 'warehouseName', l: '仓库名称', w: 120, filter: { type: 'text' } }, 
    { k: 'batchNo', l: '批次号', w: 120, filter: { type: 'text' } }, 
    { k: 'serialNo', l: '序列号', w: 130, filter: { type: 'text' } }, 
    { k: 'stockStatus', l: '库存状态', w: 90, filter: { type: 'select', options: [{ value: 'QUALIFIED', label: '合格' },{ value: 'PENDING', label: '待检' },{ value: 'DEFECTIVE', label: '不合格' },{ value: 'QUARANTINED', label: '冻结' }] } }, 
    { k: 'qty', l: '数量', w: 90, filter: { type: 'number', range: true } }, 
    { k: 'expDate', l: '到期日', w: 110, filter: { type: 'date', range: true } }, 
    { k: 'inSource', l: '入库来源', w: 110, filter: { type: 'select', options: [{ value: 'PURCHASE', label: '采购入库' },{ value: 'SALES_RETURN', label: '销退入库' },{ value: 'TRANSFER', label: '调拨入库' },{ value: 'INIT', label: '期初库存' },{ value: 'ADJUST', label: '库存调整' },{ value: 'PRODUCTION', label: '生产入库' }] } }
  ]
}

const salesOuts = {
  key: 'sales-outs', title: '销售出库', api: '/api/sales-outs', detailable: true, detailPath: '/sales-out-edit', viewPath: '/sales-out-edit', readonlyQuery: true, importable: false, exportable: false, noEdit: true, noCreate: true, noDelete: true, maxActions: 2,
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
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
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
  key: 'receipts', title: '收货入库', api: '/api/receipts', detailable: true, detailPath: '/receipts', businessType: 'receipt', importable: false, exportable: true, noEdit: true, noCreate: true, noDelete: true, maxActions: 3,
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
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
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
  key: 'stock-moves', title: '库存移动', api: '/api/stock-moves', detailable: true, detailPath: '/stock-moves', businessType: 'stockMove',
  noEdit: true, noDelete: true, importable: false, exportable: true, createPath: '/stock-move-edit/new', createPermission: 'stock_move:create',
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '移动单号', w: 170, filter: { type: 'text' } },
    { k: 'moveType', l: '移动类型', w: 120, filter: { type: 'select', options: [
      { value: 'STATUS_ADJUST', label: '仓内状态调整' },
      { value: 'WAREHOUSE_TRANSFER', label: '跨仓移动' }
    ] } },
    { k: 'fromWarehouseName', l: '源仓库', w: 140, filter: { type: 'text' } },
    { k: 'toWarehouseName', l: '目标仓库', w: 140, filter: { type: 'text' } },
    { k: 'fromStockStatus', l: '源状态', w: 90, filter: { type: 'select', options: [
      { value: 'QUALIFIED', label: '合格' }, { value: 'DEFECTIVE', label: '不合格' },
      { value: 'QUARANTINED', label: '隔离' }, { value: 'PENDING', label: '待检' }
    ] } },
    { k: 'toStockStatus', l: '目标状态', w: 90, filter: { type: 'select', options: [
      { value: 'QUALIFIED', label: '合格' }, { value: 'DEFECTIVE', label: '不合格' },
      { value: 'QUARANTINED', label: '隔离' }, { value: 'PENDING', label: '待检' }
    ] } },
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [
      { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }
    ] } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
  ],
  statusActions: {
    COMPLETED: [{ key: 'open', label: '查看', path: '/stock-move-edit', type: 'primary', isRoute: true }],
    CANCELLED: [{ key: 'open', label: '查看', path: '/stock-move-edit', type: 'default', isRoute: true }]
  },
  actions: [{ key: 'open', label: '查看', path: '/stock-move-edit', type: 'primary', isRoute: true }]
}

const STOCK_LINE_COLS = [
  { k: 'productId', l: '产品SKU', type: 'picker', picker: 'products', displayKey: 'productName', required: true },
  { k: 'batchNo', l: '批号' },
  { k: 'serialNo', l: '序列号' },
  { k: 'qty', l: '调整数量', type: 'number', required: true, precision: 4 },
  { k: 'remark', l: '备注' }
]

const inventoryAdjustments = {
  key: 'inventory-adjustments', title: '库存调整', api: '/api/inventory-adjustments', detailable: true, detailPath: '/inventory-adjustments', businessType: 'inventoryAdjustment', createPermission: 'inventory_adjustment:create', noEdit: true,
  importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '调整单号', w: 160, filter: { type: 'text' } }, 
    { k: 'category', l: '调整方向', w: 90, filter: { type: 'select', options: [{ value: 'IN', label: '盘盈(增加)' }, { value: 'OUT', label: '盘亏(扣减)' }] } }, 
    { k: 'type', l: '调整类型', w: 110, filter: { type: 'select', options: [{ value: 'STOCKTAKE', label: '盘点差异' }, { value: 'DAMAGE', label: '报损' }, { value: 'CORRECT', label: '数据修正' }, { value: 'OTHER', label: '其他' }] } }, 
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'CONFIRMED', label: '已确认' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }] } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
  ],
  form: [
    { key: 'warehouseId', label: '仓库', required: true, picker: 'warehouses', group: '调整信息' },
    { key: 'category', label: '调整方向', type: 'select', required: true, value: 'IN', group: '调整信息', options: [{ value: 'IN', label: '盘盈(增加)' }, { value: 'OUT', label: '盘亏(扣减)' }] },
    { key: 'type', label: '调整类型', type: 'select', required: true, value: 'STOCKTAKE', group: '调整信息', options: [{ value: 'STOCKTAKE', label: '盘点差异' }, { value: 'DAMAGE', label: '报损' }, { value: 'CORRECT', label: '数据修正' }, { value: 'OTHER', label: '其他' }] },
    { key: 'stockStatus', label: '库存状态', type: 'select', value: 'QUALIFIED', group: '调整信息', options: [{ value: 'QUALIFIED', label: '合格' }, { value: 'PENDING', label: '待检' }, { value: 'DEFECTIVE', label: '不合格' }] },
    { key: 'remark', label: '原因说明', type: 'textarea', required: true, group: '其它' },
    { key: 'lines', type: 'lines', label: '调整明细', required: true, group: '调整明细', cols: STOCK_LINE_COLS }
  ]
}

const surgeryReports = {
  key: 'surgery-reports', title: '手术植入报台', api: '/api/surgery-reports', detailable: true, detailPath: '/surgery-reports', businessType: 'surgeryReport', createPermission: 'surgery_report:create',
  importable: true, exportable: true,
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '报台单号', w: 160, filter: { type: 'text' } },
    { k: 'dealerName', l: '经销商', w: 120, filter: { type: 'text' } },
    { k: 'terminalName', l: '医院', w: 120, filter: { type: 'text' } },
    { k: 'surgeryDate', l: '手术日期', w: 110, filter: { type: 'date', range: true } },
    { k: 'doctorName', l: '主刀医生', w: 90, filter: { type: 'text' } },
    { k: 'attachmentName', l: '附件', w: 160, filter: { type: 'text' } },
    { k: 'status', l: '状态', w: 90, filter: { type: 'select', options: [{ value: 'DRAFT', label: '草稿' }, { value: 'CONFIRMED', label: '已确认' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }] } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
  ],
  form: [
    { key: 'dealerId', label: '经销商', required: true, picker: 'dealers', group: '手术信息' },
    { key: 'terminalId', label: '医院/终端(须已授权)', required: true, picker: 'hospitals', group: '手术信息' },
    // v3.8.7 经销商报台去除仓库（与厂家库存无关）
    { key: 'surgeryDate', label: '手术日期', type: 'date', required: true, group: '手术信息' },
    { key: 'patientInfo', label: '患者信息', group: '手术详情', placeholder: '如：李某某，男，65岁' },
    { key: 'doctorName', label: '主刀医生', group: '手术详情' },
    { key: 'remark', label: '手术备注', type: 'textarea', group: '其它' },
    { key: 'attachment', label: '附件', type: 'attachment', group: '其它' },
    { key: 'lines', type: 'lines', importable: true, serialPasteSplit: true, label: '植入产品明细', required: true, group: '植入明细', cols: [
      { k: 'productId', l: '产品', type: 'picker', picker: 'products', format: 'productName' },
      { k: 'qty', l: '数量', type: 'number' },
      { k: 'batchNo', l: '批次号', type: 'batch' },
      { k: 'serialNo', l: '序列号', type: 'serial' },
      { k: 'unitPrice', l: '单价', type: 'number' }
    ] }
  ]
}

const users = {
  key: 'users', title: '账号管理', api: '/api/users', createPermission: 'user:create',
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'username', l: '账号', w: 130, filter: { type: 'text' } },
    { k: 'name', l: '姓名', w: 110, filter: { type: 'text' } },
    { k: 'roleName', l: '角色', w: 120 },
    { k: 'userType', l: '类型', w: 80, filter: { type: 'select', options: [{ value: 'vendor', label: '厂商' }, { value: 'dealer', label: '经销商' }] } },
    { k: 'email', l: '邮箱', filter: { type: 'text' } },
    { k: 'phone', l: '手机号', w: 130 },
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: [{ value: 'active', label: '启用' }, { value: 'inactive', label: '停用' }, { value: 'locked', label: '锁定' }] } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
  ],
  form: [
    { key: 'username', label: '账号', required: true, group: '基本信息' },
    { key: 'name', label: '姓名', required: true, group: '基本信息' },
    { key: 'userType', label: '用户类型', type: 'select', required: true, value: 'vendor', group: '基本信息', options: [{ value: 'vendor', label: '厂商' }, { value: 'dealer', label: '经销商' }] },
    { key: 'password', label: '初始密码', type: 'password', group: '基本信息', placeholder: '新增时必填，至少 8 位' },
    { key: 'roleId', label: '角色', type: 'select', required: true, group: '权限', optionsUrl: '/api/roles', optionValue: 'id', optionLabel: 'name', placeholder: '请选择角色' },
    { key: 'email', label: '邮箱', type: 'email', required: true, group: '联系信息' },
    { key: 'phone', label: '手机号', required: true, type: 'text', group: '联系信息' },
    { key: 'orgId', label: '所属组织', picker: 'org-units', group: '归属' },
    { key: 'dealerId', label: '所属经销商(dealer)', picker: 'dealers', group: '归属' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '状态', options: [{ value: 'active', label: '启用' }, { value: 'inactive', label: '停用' }, { value: 'locked', label: '锁定' }] }
  ]
}

const positions = {
  key: 'positions', title: '销售岗位', api: '/api/sales-positions', createPermission: 'position:create',
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } },
    { k: 'code', l: '岗位编码', w: 130, filter: { type: 'text' } },
    { k: 'name', l: '岗位名称', filter: { type: 'text' } },
    { k: 'parentId', l: '上级岗位', w: 120 },
    { k: 'level', l: '级别', w: 80, filter: { type: 'text' } },
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } },
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } },
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
  ],
  form: [
    { key: 'code', label: '岗位编码', required: true, group: '基本信息' },
    { key: 'name', label: '岗位名称', required: true, group: '基本信息' },
    { key: 'parentId', label: '上级岗位', picker: 'positions', group: '基本信息' },
    { key: 'level', label: '岗位级别', type: 'number', value: 1, group: '基本信息' },
    { key: 'sortOrder', label: '排序', type: 'number', value: 0, group: '基本信息' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '状态', options: S_ACTIVE }
  ]
}

const roles = {
  key: 'roles', title: '角色管理', api: '/api/roles', createPermission: 'role:create',
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, 
    { k: 'code', l: '编码', filter: { type: 'text' } }, 
    { k: 'name', l: '名称', filter: { type: 'text' } }, 
    { k: 'type', l: '类型', w: 90, filter: { type: 'select', options: [{ value: 'system', label: '系统角色' }, { value: 'custom', label: '自定义角色' }] } }, 
    { k: 'description', l: '描述', filter: { type: 'text' } }, 
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } }, 
    { k: 'updatedAt', l: '更新时间', w: 160, filter: { type: 'datetime', range: true } }
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
    { k: 'lastOrderAt', l: '最近下单', w: 130, filter: { type: 'date', range: true } }
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
    { k: 'lastSurgeryAt', l: '最近手术', w: 130, filter: { type: 'date', range: true } }
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
    { k: 'createdAt', l: '创建时间', w: 160, filter: { type: 'datetime', range: true } },
    { k: 'approvedAt', l: '审批时间', w: 160, filter: { type: 'date', range: true } },
    { k: 'shippedAt', l: '发货时间', w: 160, filter: { type: 'date', range: true } },
    { k: 'completedAt', l: '完成时间', w: 160, filter: { type: 'date', range: true } }
  ]
}

const productLines = {
  key: 'product-lines', title: '产品层次', api: '/api/product-lines', detailable: true, detailPath: '/product-lines', businessType: 'productLine', createPermission: 'product_line:create',
  cols: [
    { k: 'id', l: '编号', w: 60, filter: { type: 'number' } }, { k: 'code', l: '层次编码', w: 130, filter: { type: 'text' } }, { k: 'name', l: '层次名称', filter: { type: 'text' } },
    { k: 'parentName', l: '上级层次', w: 160 },
    { k: 'level', l: '产品层次', w: 100, filter: { type: 'select', options: [{ value: 1, label: '产品层次1' }, { value: 2, label: '产品层次2' }, { value: 3, label: '产品层次3' }] } },
    { k: 'sortOrder', l: '排序', w: 80 }, { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } }, { k: 'updatedAt', l: '更新时间', w: 160 }
  ],
  form: [
    { key: 'code', label: '层次编码', required: true, group: '基本信息' }, { key: 'name', label: '层次名称', required: true, group: '基本信息' },
    { key: 'parentId', label: '上级产品层次', picker: 'product-lines', group: '基本信息' },
    { key: 'level', label: '产品层次', type: 'select', required: true, value: 1, group: '基本信息', options: [{ value: 1, label: '产品层次1' }, { value: 2, label: '产品层次2' }, { value: 3, label: '产品层次3' }] },
    { key: 'description', label: '描述', type: 'textarea', group: '基本信息' }, { key: 'sortOrder', label: '排序', type: 'number', value: 0, group: '基本信息' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '状态', options: S_ACTIVE }
  ]
}

const productBundles = {
  noDelete: true, key: 'product-bundles', title: '产品组合(BOM)', api: '/api/product-bundles', detailable: true, detailPath: '/product-bundles', businessType: 'product_bundle', createPermission: 'product_bundle:create',
  editableWhen: ['draft'],
  rowActions: [
    { key: 'edit', label: '编辑', type: 'primary', versionStatusIn: ['draft'] },
    { key: 'newVersion', label: '新建版本', type: 'primary', versionStatusIn: ['active'], confirm: '确认基于当前版本新建草稿版本？原版本保持当前状态。' },
    { key: 'activateDraft', label: '发布', type: 'success', versionStatusIn: ['draft'], confirm: '确认发布此草稿版本？发布后原版本将变为历史版本。' }
  ],
  deletableWhen: [],
  hideRowActions: ['delete'],
  rowActionHandlers: {
    newVersion(row, _b, { openForm, refresh }) {
      ElMessageBox.confirm('确认基于当前版本新建草稿版本？原版本保持当前状态，草稿可编辑子件。', '提示', { type: 'warning' })
        .then(async () => {
          const res = await actionResource('/api/product-bundles', row.id, '/new-version', 'POST');
          const draft = res && res.data ? res.data : res;
          ElMessage.success('已创建草稿新版本，可继续编辑子件');
          if (openForm && draft && draft.id != null) { openForm(draft); if (refresh) refresh(); }
          else location.reload();
        })
        .catch(() => {});
    },
    activateDraft(row, _b, { refresh }) {
      ElMessageBox.confirm('确认发布此草稿版本？发布后原版本将变为历史版本。', '提示', { type: 'warning' })
        .then(async () => { await actionResource('/api/product-bundles', row.id, '/activate', 'POST'); ElMessage.success('版本已发布'); if (refresh) refresh(); else location.reload(); })
        .catch(() => {});
    }
  },
  cols: [
    { k: 'id', l: '编号', w: 60 },
    { k: 'code', l: 'BOM编码', w: 150, filter: { type: 'text' } },
    { k: 'name', l: 'BOM名称', minWidth: 160, filter: { type: 'text' } },
    { k: 'productCode', l: '母件SKU编码', w: 140, sortable: true },
    { k: 'productName', l: '母件SKU名称', w: 180, showOverflowTooltip: true },
    { k: 'bomVersion', l: '版本', w: 90 },
    { k: 'versionStatus', l: '版本状态', w: 100, tag: (r) => ({ type: r.versionStatus === 'active' ? 'success' : 'info' }) },
    { k: 'validFrom', l: '生效开始', w: 170 }, { k: 'validTo', l: '生效结束', w: 170 },
    { k: 'status', l: '状态', w: 80, filter: { type: 'select', options: S_ACTIVE } }
  ],
  form: [
    { key: 'productId', label: 'BOM母件SKU', required: true, type: 'product-picker', group: '基本信息', readonlyOnEdit: true },
    { key: 'code', label: 'BOM编码', required: true, group: '基本信息', readonlyOnEdit: true },
    { key: 'name', label: 'BOM名称', required: true, group: '基本信息', readonlyOnEdit: true },
    { key: 'bomVersion', label: 'BOM版本', group: '版本管理', value: '1', readonly: true },
    { key: 'versionStatus', label: '版本状态', type: 'select', value: 'active', group: '版本管理', readonly: true, options: [{ value: 'active', label: '当前版本' }, { value: 'history', label: '历史版本' }, { value: 'draft', label: '草稿' }] },
    { key: 'validFrom', label: '生效开始', type: 'date', group: '有效期' },
    { key: 'validTo', label: '生效结束', type: 'date', group: '有效期' },
    { key: 'allowSplit', label: '允许子件分开发货', type: 'boolean', value: true, group: '发货控制' },
    { key: 'description', label: '说明', type: 'textarea', group: '其他' },
    { key: 'status', label: '状态', type: 'select', value: 'active', group: '其他', options: S_ACTIVE },
    { key: 'lines', type: 'lines', label: 'BOM子件', required: true, group: '子件维护', cols: [
      { k: 'childProductId', l: '子件SKU', type: 'picker', picker: 'products', displayKey: 'childProductName', required: true },
      { k: 'quantity', l: '数量', type: 'number', required: true, precision: 4, min: 1 },
      { k: 'lineType', l: '子件类型', type: 'select', value: 'FIXED', options: [{ value: 'FIXED', label: '固定子件' }, { value: 'OPTIONAL', label: '可选子件' }] },
      { k: 'isRequired', l: '是否必选', type: 'boolean', value: true },
      { k: 'description', l: '说明' }
    ] }
  ]
}

import { V430_MODULES } from './v430-modules.js'

export const LAYOUT_PARAM_MAPS = {
  orders: { dealer: 'dealerId', dateFrom: 'createdFrom', dateTo: 'createdTo' },
  'sales-returns': { dateFrom: 'createdAtFrom', dateTo: 'createdAtTo' },
  'purchase-orders': { supplier: 'supplierId', dateFrom: 'createdAtFrom', dateTo: 'createdAtTo' },
  'purchase-returns': { supplier: 'supplierId', dateFrom: 'createdAtFrom', dateTo: 'createdAtTo' },
  'sales-outs': { dateFrom: 'salesDateFrom', dateTo: 'salesDateTo' },
  receipts: { dateFrom: 'receiptDateFrom', dateTo: 'receiptDateTo' },
  'stock-moves': { dateFrom: 'createdAtFrom', dateTo: 'createdAtTo' },
  'surgery-reports': { dateFrom: 'surgeryDateFrom', dateTo: 'surgeryDateTo' },
  contracts: { dateFrom: 'createdAtFrom', dateTo: 'createdAtTo' },
  products: { category: 'categoryId' },
  inventory: { warehouse: 'warehouseId' },
  'dealer-profile': { region: 'regionId' },
  'api-call-log': { dateFrom: 'startTime', dateTo: 'endTime', status: 'statusCode' }
}

export const MODULE_CONFIGS = {
  products, categories, dealers, hospitals, warehouses, regions, suppliers,
  'product-lines': productLines, 'product-bundles': productBundles,
  'product-prices': productPrices,
  authorizations, promotions,
  orders, 'sales-returns': salesReturns, 'purchase-orders': purchaseOrders, 'purchase-returns': purchaseReturns,
  inventory, 'sales-outs': salesOuts, receipts, 'stock-moves': stockMoves, 'inventory-adjustments': inventoryAdjustments,
  'surgery-reports': surgeryReports, users, positions, roles,
  'report-sales-ranking': reportSalesRanking, 'report-product-top10': reportProductTop10,
  'report-inventory-turnover': reportInventoryTurnover, 'report-surgery-stats': reportSurgeryStats,
  'report-receivables': reportReceivables, 'report-order-trace': reportOrderTrace,
  ...V430_MODULES
}
