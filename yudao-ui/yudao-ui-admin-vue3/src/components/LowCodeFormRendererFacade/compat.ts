import { FieldType, type FormFieldConfig } from '@/api/lowcode'
import type { VersionedFormConfig } from '@/components/LowCodeFormRenderer/rendererVersion'

/**
 * 将当前设计器的顶层 componentName 兼容为 V1 历史渲染器可识别的 props.componentName。
 *
 * 该转换只发生在运行时浅拷贝上：
 * - 不修改传入配置；
 * - 已有历史 props.componentName 优先；
 * - 无需兼容时保持原对象引用，避免无意义重建。
 */
export function normalizeRendererConfig(config: VersionedFormConfig): VersionedFormConfig {
  let changed = false
  const fields = (config.fields || []).map((field) => normalizeCustomField(field, () => {
    changed = true
  }))
  return changed ? { ...config, fields } : config
}

function normalizeCustomField(
  field: FormFieldConfig,
  onChanged: () => void
): FormFieldConfig {
  if (
    field.type !== FieldType.CUSTOM ||
    !field.componentName ||
    field.props?.componentName
  ) {
    return field
  }
  onChanged()
  return {
    ...field,
    props: {
      ...(field.props || {}),
      componentName: field.componentName
    }
  }
}
