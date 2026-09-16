/**
 * 全局类型导出入口与通用工具类型。
 *
 * <p>消费方应优先从此文件 import 类型，而非直接从 api.d.ts import，
 * 以保持模块边界稳定。</p>
 */

// 重新导出 API 类型，使消费方可通过 `import type { Result, Project } from '@/types'` 复用
export * from './api'

/**
 * Element Plus 标签 / 按钮等组件通用的「语义类型」联合。
 *
 * 用于消除 view 层 `tagType: any` 的常见模式，
 * 与 el-tag / el-button 的 `type` prop 取值保持一致。
 */
export type EpTagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

/** 将 T 的所有属性变为可选 */
export type Optional<T> = {
  [P in keyof T]?: T[P]
}

/** 将 T 的所有属性变为可空（保留可选语义并允许 null） */
export type Nullable<T> = {
  [P in keyof T]: T[P] | null
}

/** 深度可选：递归将所有属性变为可选 */
export type DeepPartial<T> = T extends object
  ? {
      [P in keyof T]?: DeepPartial<T[P]>
    }
  : T

/** 深度只读：递归将所有属性变为 readonly */
export type DeepReadonly<T> = T extends object
  ? {
      readonly [P in keyof T]: DeepReadonly<T[P]>
    }
  : T

/** 提取 T 中类型为 U 的属性键 */
export type PickByValueType<T, U> = {
  [P in keyof T as T[P] extends U ? P : never]: T[P]
}

/** 让 T 的指定键 K 变为可选，其余保持原样 */
export type WithOptional<T, K extends keyof T> = Omit<T, K> & Partial<Pick<T, K>>

/** 让 T 的指定键 K 变为必选，其余保持原样 */
export type WithRequired<T, K extends keyof T> = T & Required<Pick<T, K>>
