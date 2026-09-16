/**
 * 低代码组件 SDK 运行时（批次4-T6）。
 *
 * <p>包装 LowCodeComponentRegistry，提供面向第三方开发者的 API：
 * <ul>
 *   <li>{@link defineLowCodeComponent}：高阶组件定义函数，规范自定义组件的注册结构</li>
 *   <li>{@link initRemoteComponents}：从后端拉取市场组件元数据并动态注册（按 entryUrl 远程加载）</li>
 *   <li>原 registry 的 register/get/has/list/initBuiltinComponents 直接透传</li>
 * </ul>
 * </p>
 *
 * <p>设计借鉴 Power Apps PCF 的 ComponentFramework.defineComponent 与 ToolJet 的
 * registerComponent，使自定义组件代码与平台核心解耦，便于独立打包与第三方分发。</p>
 */
import type { Component } from 'vue'
import LowCodeComponentRegistry from '@/components/LowCodeComponentRegistry'
import type { ComponentMeta, RegisteredComponent } from '@/components/LowCodeComponentRegistry/types'
import { listComponentMetas } from '@/api/lowcode-component-meta'
import { isAllowedUrl } from './csp-allowlist'

/** 透传 registry 基础 API */
export const register = LowCodeComponentRegistry.register
export const get = LowCodeComponentRegistry.get
export const has = LowCodeComponentRegistry.has
export const list = LowCodeComponentRegistry.list
export const initBuiltinComponents = LowCodeComponentRegistry.initBuiltinComponents

/**
 * 自定义组件定义结构。
 *
 * <p>由第三方开发者构造此对象后调用 {@link defineLowCodeComponent} 完成注册，
 * 等价于：
 * <pre>
 * LowCodeComponentRegistry.register(def.name, def.component, def.meta)
 * </pre>
 * 但通过本函数可获得：
 * <ul>
 *   <li>propsSchema 与 component.props 的一致性校验（开发态 warn）</li>
 *   <li>meta 缺省字段补全（displayName 回退 name、category 回退 'CUSTOM'）</li>
 *   <li>返回 RegisteredComponent 便于组件库自管理</li>
 * </ul>
 * </p>
 */
export interface LowCodeComponentDefinition {
  /** 组件名（注册 key，全局唯一） */
  name: string
  /** Vue3 组件对象（defineComponent 返回值或 setup 函数） */
  component: Component
  /** 组件元数据 */
  meta: ComponentMeta
}

/**
 * 定义并注册一个低代码自定义组件（批次4-T6 核心 API）。
 *
 * <p>使用示例见 {@link ./index.ts} 顶部 Javadoc。</p>
 *
 * @param def 组件定义
 * @returns 已注册的组件记录
 */
export function defineLowCodeComponent(
  def: LowCodeComponentDefinition
): RegisteredComponent {
  if (!def.name || typeof def.name !== 'string') {
    throw new Error('[LowCodeSDK] defineLowCodeComponent: name 必填且为字符串')
  }
  if (!def.component) {
    throw new Error(`[LowCodeSDK] defineLowCodeComponent: ${def.name} 缺少 component`)
  }
  // meta 缺省补全
  const meta: ComponentMeta = {
    name: def.name,
    displayName: def.meta.displayName || def.meta.name || def.name,
    category: def.meta.category || 'CUSTOM',
    propsSchema: def.meta.propsSchema || []
  }
  // 开发态一致性校验：propsSchema 的 key 应在 component.props 中存在
  if (import.meta.env?.DEV && meta.propsSchema.length > 0) {
    const propKeys = new Set<string>(['modelValue', 'disabled', 'readonly', 'placeholder'])
    const compProps = (def.component as any)?.props
    if (compProps && typeof compProps === 'object') {
      Object.keys(compProps).forEach((k) => propKeys.add(k))
    }
    for (const pd of meta.propsSchema) {
      if (!propKeys.has(pd.key)) {
        console.warn(
          `[LowCodeSDK] 组件 "${def.name}" 的 propsSchema 含 key "${pd.key}"，` +
            `但 component.props 中未声明，运行时将无法接收该 prop 值`
        )
      }
    }
  }
  register(def.name, def.component, meta)
  return { component: def.component, meta }
}

/**
 * 从后端拉取市场组件元数据并动态注册（批次4-T6）。
 *
 * <p>拉取 sourceType=MARKETPLACE 的组件记录，按 entryUrl 动态 import 组件 JS，
 * 注册到 registry。失败的单个组件仅记 warn 日志，不阻断整体加载。</p>
 *
 * <p>安全性：远程组件 entryUrl 必须满足以下条件才会加载：
 * <ul>
 *   <li>URL 以 https:// 开头（生产强制）或 http://localhost（开发环境）</li>
 *   <li>URL 域名在 CSP 白名单内（由后端 component-meta.entryUrl 校验，前端再防御性校验）</li>
 *   <li>动态 import 失败时仅 warn，不 throw</li>
 * </ul>
 * 实际的 iframe 沙箱隔离由 B4-T7 ComponentSandbox 负责，本函数仅负责组件 JS 注册。</p>
 *
 * <p>注意：本函数依赖原生 ES Module 动态 import，需目标服务器配置 CORS 允许跨域。
 * 对于不支持 CORS 的源，建议改用 B4-T7 的 iframe 沙箱方案。</p>
 */
export async function initRemoteComponents(): Promise<void> {
  let metas: Awaited<ReturnType<typeof listComponentMetas>>
  try {
    metas = await listComponentMetas()
  } catch (e) {
    console.warn('[LowCodeSDK] 拉取远程组件元数据失败，跳过远程组件加载', e)
    return
  }
  for (const meta of metas) {
    // 仅加载市场组件且有 entryUrl
    if (meta.sourceType !== 'MARKETPLACE' || !meta.entryUrl) continue
    if (!isAllowedUrl(meta.entryUrl)) {
      console.warn(
        `[LowCodeSDK] 跳过远程组件 "${meta.name}"：entryUrl 不在允许范围 (CSP 白名单/协议校验失败): ${meta.entryUrl}`
      )
      continue
    }
    try {
      const mod = await import(/* @vite-ignore */ meta.entryUrl)
      const component = mod.default
      if (!component) {
        console.warn(`[LowCodeSDK] 远程组件 "${meta.name}" 模块未导出 default，跳过`)
        continue
      }
      // propsSchema 从后端拉取的字符串解析回对象
      let propsSchema: ComponentMeta['propsSchema'] = []
      if (meta.propsSchema) {
        try {
          propsSchema = JSON.parse(meta.propsSchema)
        } catch {
          console.warn(`[LowCodeSDK] 远程组件 "${meta.name}" propsSchema JSON 解析失败，按空 schema 处理`)
        }
      }
      register(meta.name, component, {
        name: meta.name,
        displayName: meta.displayName || meta.name,
        category: meta.category || 'CUSTOM',
        propsSchema
      })
    } catch (e) {
      console.warn(`[LowCodeSDK] 加载远程组件 "${meta.name}" 失败: ${meta.entryUrl}`, e)
    }
  }
}
