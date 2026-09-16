import { get, post } from '@/utils/request'

/**
 * 低代码组件元数据（与后端 LowCodeComponentMeta 实体对齐，批次4-T6/T8）。
 *
 * <p>批次4-T8 已新增 sourceType / entryUrl / version / author / status / tags /
 * downloadCount 字段，用于支持组件市场。批次4-T6 在前端补全这些字段的类型定义，
 * 使 SDK 的 initRemoteComponents 能正确读取。</p>
 */
export interface LowCodeComponentMeta {
  id: number
  name: string
  displayName: string
  category: string
  /** 属性 JSON Schema（字符串形式，前端使用时 JSON.parse） */
  propsSchema: string
  description?: string
  /** 图标 */
  icon?: string
  /** 是否内置组件（1 是 / 0 否） */
  builtin: number
  /** 组件版本（批次4-T8） */
  version?: string
  /** 作者（批次4-T8） */
  author?: string
  /** 状态: PUBLISHED / DRAFT / ARCHIVED（批次4-T8） */
  status?: string
  /** 标签（逗号分隔，批次4-T8） */
  tags?: string
  /** 下载量（批次4-T8） */
  downloadCount?: number
  /** 来源: BUILTIN / CUSTOM / MARKETPLACE（批次4-T8） */
  sourceType?: string
  /** 远程组件入口 URL（MARKETPLACE 类型，批次4-T8） */
  entryUrl?: string
}

/**
 * 查询所有组件元数据（别名，与后端 /api/lowcode/component-meta GET 对齐）。
 *
 * <p>批次4-T6 新增此别名以匹配 SDK runtime 的命名规范。</p>
 */
export function listComponentMetas() {
  return get<LowCodeComponentMeta[]>('/api/lowcode/component-meta')
}

/** 兼容旧调用名（向后保留） */
export const getComponentMetaList = listComponentMetas

/**
 * 下载组件（增加下载计数并返回组件信息，批次4-T8 后端 endpoint）。
 */
export function downloadComponent(id: number) {
  return post<LowCodeComponentMeta>(`/api/lowcode/component-meta/${id}/download`)
}
