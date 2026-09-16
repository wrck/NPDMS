import type { FormConfig } from '@/api/lowcode'

/**
 * 表单渲染器版本。
 *
 * 历史配置没有 rendererVersion，因此任何缺省/非法值都必须回退到 V1。
 * 只有显式声明 V2 才切换到 FormCreate 渲染器。
 */
export const LowCodeFormRendererVersion = {
  V1: 'v1',
  V2: 'v2'
} as const

export type LowCodeFormRendererVersion =
  (typeof LowCodeFormRendererVersion)[keyof typeof LowCodeFormRendererVersion]

/** 在不改变现有 FormConfig 公共类型的前提下扩展可选渲染版本。 */
export type VersionedFormConfig = FormConfig & {
  rendererVersion?: string | null
}

export function normalizeLowCodeFormRendererVersion(value: unknown): LowCodeFormRendererVersion {
  if (typeof value === 'string' && value.trim().toLowerCase() === LowCodeFormRendererVersion.V2) {
    return LowCodeFormRendererVersion.V2
  }
  return LowCodeFormRendererVersion.V1
}

/**
 * 解析最终渲染版本。
 *
 * 显式 prop override 优先；空 override 继续读取配置。历史配置及未知值一律使用 V1，
 * 确保升级代码不会自动改变已发布表单的渲染行为。
 */
export function resolveLowCodeFormRendererVersion(
  config: FormConfig,
  override?: unknown
): LowCodeFormRendererVersion {
  const hasOverride =
    override !== undefined &&
    override !== null &&
    !(typeof override === 'string' && override.trim() === '')
  if (hasOverride) {
    return normalizeLowCodeFormRendererVersion(override)
  }
  return normalizeLowCodeFormRendererVersion((config as VersionedFormConfig).rendererVersion)
}

/**
 * 将设计器选择的渲染版本写回表单配置。
 *
 * V1 是历史兼容默认值，必须通过“字段缺省”表达，不能主动持久化 rendererVersion=v1；
 * 只有显式选择 V2 时才写入 rendererVersion=v2。这样打开并保存历史表单不会产生无意义的版本变更。
 */
export function setLowCodeFormRendererVersion(
  config: FormConfig,
  value: unknown
): LowCodeFormRendererVersion {
  const normalized = normalizeLowCodeFormRendererVersion(value)
  const versionedConfig = config as VersionedFormConfig
  if (normalized === LowCodeFormRendererVersion.V2) {
    versionedConfig.rendererVersion = LowCodeFormRendererVersion.V2
  } else {
    delete versionedConfig.rendererVersion
  }
  return normalized
}
