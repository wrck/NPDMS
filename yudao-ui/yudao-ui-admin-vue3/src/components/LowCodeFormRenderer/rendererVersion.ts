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
