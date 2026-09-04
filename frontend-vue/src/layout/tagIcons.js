// RuoYi 风格页签图标：按页签标题/路径匹配 Element Plus 图标名（字符串，由 <component :is> 解析）
const RULES = [
  { test: /(工作台|首页|home)/i, icon: 'HomeFilled' },
  { test: /(产品|商品|product|goods|bom|组合|分类|产品线)/i, icon: 'Goods' },
  { test: /(经销商|客户|医院|区域|供应商|dealer|customer|supplier|hospital)/i, icon: 'OfficeBuilding' },
  { test: /(销售订单|订单|下单|order)/i, icon: 'Sell' },
  { test: /(销退|退货|return)/i, icon: 'RefreshLeft' },
  { test: /(出库|发货|ship|out)/i, icon: 'Van' },
  { test: /(入库|收货|receipt)/i, icon: 'Box' },
  { test: /(库存|寄售|stock|inventory|盘点|移动)/i, icon: 'Box' },
  { test: /(采购|purchase)/i, icon: 'ShoppingCart' },
  { test: /(合同|contract)/i, icon: 'Document' },
  { test: /(模板|template)/i, icon: 'Files' },
  { test: /(授权|auth)/i, icon: 'Key' },
  { test: /(审批|approval|委托)/i, icon: 'Stamp' },
  { test: /(报表|驾驶舱|看板|dashboard|report|分析|排行|周转|画像|应收|追溯|业绩)/i, icon: 'DataAnalysis' },
  { test: /(促销|折扣|代金券|promotion|discount|coupon)/i, icon: 'Discount' },
  { test: /(手术|报台|surgery)/i, icon: 'FirstAidKit' },
  { test: /(价格|price)/i, icon: 'PriceTag' },
  { test: /(账号|用户|account|user)/i, icon: 'User' },
  { test: /(角色|权限|role)/i, icon: 'UserFilled' },
  { test: /(岗位|position)/i, icon: 'Avatar' },
  { test: /(日志|log)/i, icon: 'Notebook' },
  { test: /(任务|导入|导出|async|task)/i, icon: 'Download' },
  { test: /(消息|通知|notification|message)/i, icon: 'Bell' },
  { test: /(配置|设置|开关|config|switch|系统)/i, icon: 'Setting' },
  { test: /(资信|账期|credit)/i, icon: 'Wallet' },
  { test: /(对码|mapping)/i, icon: 'Connection' },
  { test: /(个人|profile|资料)/i, icon: 'UserFilled' }
]

export function iconForRoute(tag) {
  if (!tag) return ''
  const hay = `${tag.title || ''} ${tag.fullPath || ''} ${tag.key || ''}`
  const hit = RULES.find((r) => r.test.test(hay))
  return hit ? hit.icon : 'Menu'
}
