import { defineStore } from 'pinia'
import { resolvePageMeta, tagKeyOf } from '@/utils/pageMeta'

const STORAGE_KEY = 'dms:tags-view'

function loadInitial() {
  const home = { key: '/home', fullPath: '/home', title: '工作台首页', affix: true }
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (raw) {
      const list = JSON.parse(raw)
      if (Array.isArray(list) && list.length) {
        return [home, ...list.filter((t) => t.key !== home.key)]
      }
    }
  } catch (e) { /* 缓存损坏则重置 */ }
  return [home]
}

export const useTagsStore = defineStore('tags', {
  state: () => ({
    tags: loadInitial(),
    activeKey: '/home',
    reloading: false
  }),
  getters: {
    closableCount: (state) => state.tags.filter((t) => !t.affix).length
  },
  actions: {
    persist() {
      try {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(this.tags))
      } catch (e) { /* 隐私模式等场景忽略 */ }
    },
    addRoute(route) {
      if (!route || route.meta?.public || route.path.startsWith('/print') || route.meta?.mobile) return
      const meta = resolvePageMeta(route)
      const key = tagKeyOf(route, meta)
      const tag = {
        key,
        fullPath: route.fullPath,
        title: meta.title
      }
      const idx = this.tags.findIndex((t) => t.key === key)
      if (idx === -1) this.tags.push(tag)
      else this.tags.splice(idx, 1, { ...this.tags[idx], ...tag })
      this.activeKey = key
      this.persist()
    },
    closeTag(key) {
      const idx = this.tags.findIndex((t) => t.key === key)
      if (idx === -1) return null
      if (this.tags[idx].affix) return null
      this.tags.splice(idx, 1)
      this.persist()
      if (this.activeKey === key) {
        const next = this.tags[idx] || this.tags[idx - 1] || this.tags[0]
        this.activeKey = next ? next.key : '/home'
        return next ? next.fullPath : '/home'
      }
      return null
    },
    closeOthers(key) {
      this.tags = this.tags.filter((t) => t.affix || t.key === key)
      this.activeKey = key
      this.persist()
    },
    closeAll() {
      this.tags = this.tags.filter((t) => t.affix)
      this.activeKey = '/home'
      this.persist()
      return '/home'
    },
    setActive(key) {
      this.activeKey = key
    },
    refreshSignal() {
      this.reloading = true
    },
    refreshDone() {
      this.reloading = false
    },
    reset() {
      this.tags = [{ key: '/home', fullPath: '/home', title: '工作台首页', affix: true }]
      this.activeKey = '/home'
      this.reloading = false
      try { sessionStorage.removeItem(STORAGE_KEY) } catch (e) { /* ignore */ }
    }
  }
})
