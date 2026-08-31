import { MENU_GROUPS } from '@/config/menu'

const HOME_CRUMB = { label: '首页', path: '/home' }

const FLAT_MENU = []
MENU_GROUPS.forEach((g) => {
  ;(g.items || []).forEach((it) => {
    FLAT_MENU.push({
      group: g.group,
      label: it.label,
      route: it.route || '/m/' + it.key,
      path: (it.route || '/m/' + it.key).split('?')[0]
    })
  })
})

const DETAIL_PARENT = [
  [/^\/dealers\/profile\//, '/dealers/profile'],
  [/^\/dealers\//, '/m/dealers'],
  [/^\/products\//, '/m/products'],
  [/^\/categories\//, '/m/categories'],
  [/^\/product-lines\//, '/m/product-lines'],
  [/^\/product-bundles\//, '/m/product-bundles'],
  [/^\/product-prices\//, '/m/product-prices'],
  [/^\/promotions\//, '/m/promotions'],
  [/^\/hospitals\//, '/m/hospitals'],
  [/^\/warehouses\//, '/m/warehouses'],
  [/^\/regions\//, '/m/regions'],
  [/^\/suppliers\//, '/m/suppliers'],
  [/^\/authorizations\//, '/m/authorizations'],
  [/^\/purchase-orders\//, '/m/purchase-orders'],
  [/^\/receipts\//, '/m/receipts'],
  [/^\/stock-moves\//, '/m/stock-moves'],
  [/^\/inventory-adjustments\//, '/m/inventory-adjustments'],
  [/^\/surgery-reports\//, '/m/surgery-reports'],
  [/^\/orders\//, '/m/orders'],
  [/^\/contracts\/templates\//, '/contracts/templates'],
  [/^\/contracts\//, '/contracts'],
  [/^\/order-create\/sales/, '/m/orders'],
  [/^\/order-create\/purchase/, '/m/purchase-orders'],
  [/^\/receipt-edit\//, '/m/receipts'],
  [/^\/stock-move-edit\//, '/m/stock-moves'],
  [/^\/sales-out-edit\//, '/m/sales-outs'],
  [/^\/sales-return-edit/, '/m/sales-returns'],
  [/^\/purchase-return-edit\//, '/m/purchase-returns']
]

function findMenuByPath(p) {
  return FLAT_MENU.find((m) => m.path === p) || null
}

function detailParentOf(path) {
  const mod = path.match(/^\/m\/([\w-]+)\//)
  if (mod) return '/m/' + mod[1]
  const hit = DETAIL_PARENT.find(([re]) => re.test(path))
  return hit ? hit[1] : null
}

export function resolvePageMeta(route) {
  const fullPath = route.fullPath
  const path = route.path

  if (path === '/home') {
    return { title: '工作台首页', isMenu: true, crumbs: [HOME_CRUMB] }
  }

  const exact =
    FLAT_MENU.find((m) => m.route === fullPath) ||
    FLAT_MENU.find((m) => m.path === path)
  if (exact) {
    return {
      title: exact.label,
      isMenu: true,
      crumbs: [HOME_CRUMB, { label: exact.group }, { label: exact.label }]
    }
  }

  const title = (route.meta && route.meta.title) || '页面'

  const prefix = FLAT_MENU
    .filter((m) => path.startsWith(m.path + '/'))
    .sort((a, b) => b.path.length - a.path.length)[0]
  if (prefix) {
    return {
      title,
      isMenu: false,
      crumbs: [HOME_CRUMB, { label: prefix.group }, { label: prefix.label, path: prefix.route }, { label: title }]
    }
  }

  const parentPath = detailParentOf(path)
  const parent = parentPath ? findMenuByPath(parentPath) : null
  if (parent) {
    return {
      title,
      isMenu: false,
      crumbs: [HOME_CRUMB, { label: parent.group }, { label: parent.label, path: parent.route }, { label: title }]
    }
  }

  return {
    title,
    isMenu: false,
    crumbs: [HOME_CRUMB, { label: title }]
  }
}

export function tagKeyOf(route, meta) {
  if (meta && meta.isMenu) return route.fullPath
  return route.name ? 'name:' + String(route.name) : route.path
}
