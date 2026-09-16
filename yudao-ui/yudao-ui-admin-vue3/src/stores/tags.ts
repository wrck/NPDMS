import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 标签视图数据结构 */
export interface View {
  path: string
  title: string
  name?: string
  /** 路由完整路径（含参数），用于激活高亮 */
  fullPath?: string
  /** 是否固定标签（不可关闭，不可拖拽移到非首位置） */
  affix?: boolean
}

/**
 * 标签页 store
 * - 管理已访问过的路由标签列表
 * - 标签持久化到 localStorage，刷新页面可恢复
 * - 支持固定标签 / 拖拽排序 / 关闭左侧 / 关闭右侧
 */
export const useTagsStore = defineStore('tags', () => {
  const TAGS_KEY = 'pms_tags_view'

  /** 固定标签的默认列表（首页始终固定） */
  const DEFAULT_AFFIX_VIEWS: View[] = [
    { path: '/dashboard', title: '首页', fullPath: '/dashboard', affix: true }
  ]

  /** 已访问视图列表（从 localStorage 恢复） */
  const visitedViews = ref<View[]>(safeParse(localStorage.getItem(TAGS_KEY) || '[]'))

  /** 安全解析 JSON，失败时返回空数组 */
  function safeParse(raw: string): View[] {
    try {
      const arr = JSON.parse(raw)
      if (!Array.isArray(arr)) return [...DEFAULT_AFFIX_VIEWS]
      // 确保首页固定标签始终存在
      const hasDashboard = arr.some((v: View) => v.path === '/dashboard')
      return hasDashboard ? arr : [...DEFAULT_AFFIX_VIEWS, ...arr]
    } catch {
      return [...DEFAULT_AFFIX_VIEWS]
    }
  }

  /** 持久化到 localStorage */
  function persist() {
    localStorage.setItem(TAGS_KEY, JSON.stringify(visitedViews.value))
  }

  /** 添加视图（已存在则跳过） */
  function addView(view: View) {
    if (visitedViews.value.some((v) => v.path === view.path)) {
      // 路径已存在时仅更新 fullPath（参数可能变化）
      const target = visitedViews.value.find((v) => v.path === view.path)
      if (target) target.fullPath = view.fullPath || view.path
      return
    }
    visitedViews.value.push({ ...view, fullPath: view.fullPath || view.path })
    persist()
  }

  /** 删除指定路径的视图（固定标签不可删除） */
  function delView(path: string) {
    const idx = visitedViews.value.findIndex((v) => v.path === path)
    if (idx === -1) return
    if (visitedViews.value[idx].affix) return // 固定标签不可删除
    visitedViews.value.splice(idx, 1)
    persist()
  }

  /** 关闭其他视图，仅保留指定路径（固定标签始终保留） */
  function delOthers(path: string) {
    visitedViews.value = visitedViews.value.filter(
      (v) => v.path === path || v.affix
    )
    persist()
  }

  /** 关闭左侧视图（固定标签始终保留） */
  function delLeft(path: string) {
    const idx = visitedViews.value.findIndex((v) => v.path === path)
    if (idx <= 0) return
    const leftViews = visitedViews.value.slice(0, idx)
    const removable = leftViews.filter((v) => !v.affix)
    const removablePaths = new Set(removable.map((v) => v.path))
    visitedViews.value = visitedViews.value.filter((v) => !removablePaths.has(v.path))
    persist()
  }

  /** 关闭右侧视图（固定标签始终保留） */
  function delRight(path: string) {
    const idx = visitedViews.value.findIndex((v) => v.path === path)
    if (idx === -1 || idx === visitedViews.value.length - 1) return
    const rightViews = visitedViews.value.slice(idx + 1)
    const removable = rightViews.filter((v) => !v.affix)
    const removablePaths = new Set(removable.map((v) => v.path))
    visitedViews.value = visitedViews.value.filter((v) => !removablePaths.has(v.path))
    persist()
  }

  /** 关闭全部视图（固定标签始终保留） */
  function delAll() {
    visitedViews.value = visitedViews.value.filter((v) => v.affix)
    persist()
  }

  /**
   * 拖拽排序：将 fromPath 移动到 toPath 位置。
   * 固定标签位置不变（被拖到固定标签前面或被拖出固定标签区时忽略）。
   */
  function moveView(fromPath: string, toPath: string) {
    const fromIdx = visitedViews.value.findIndex((v) => v.path === fromPath)
    const toIdx = visitedViews.value.findIndex((v) => v.path === toPath)
    if (fromIdx === -1 || toIdx === -1 || fromIdx === toIdx) return
    // 固定标签不可拖动
    if (visitedViews.value[fromIdx].affix) return
    // 不允许拖到固定标签前面（保证固定标签始终在前）
    const firstNonAffixIdx = visitedViews.value.findIndex((v) => !v.affix)
    if (toIdx < firstNonAffixIdx) return
    const [moved] = visitedViews.value.splice(fromIdx, 1)
    // 重新计算 toIdx（splice 后索引可能变化）
    const newToIdx = visitedViews.value.findIndex((v) => v.path === toPath)
    visitedViews.value.splice(newToIdx, 0, moved)
    persist()
  }

  return {
    visitedViews,
    addView,
    delView,
    delOthers,
    delLeft,
    delRight,
    delAll,
    moveView
  }
})
