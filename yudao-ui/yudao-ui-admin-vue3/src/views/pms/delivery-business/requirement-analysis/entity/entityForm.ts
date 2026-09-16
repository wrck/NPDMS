import type { View, Patch } from '@/api/pms/engineering/requirement-analysis/entity'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'

export const formValues = (detail: View): JsonObject => {
  if (!detail.form) return { ...detail.values }
  return Object.fromEntries(Object.entries(detail.form.binding.fieldBindings).map(([field, code]) => [field, detail.values[code] ?? null]))
}
export const businessPatch = (detail: View, submitted: JsonObject): Patch => {
  const fixed = new Set(detail.fieldCatalog.map(field => field.code))
  const values: JsonObject = {}
  const extensionValues = Object.fromEntries(Object.entries(detail.values).filter(([code]) => !fixed.has(code)))
  let extensionsChanged = false
  for (const [field, value] of Object.entries(submitted)) {
    const code = detail.form ? detail.form.binding.fieldBindings[field] : field
    if (!code) throw new Error(`字段未绑定：${field}`)
    if (fixed.has(code)) values[code] = value
    else { extensionValues[code] = value; extensionsChanged = true }
  }
  return { values, expectedExtensionVersion: detail.extensionValueVersion,
    extensionDefinitionRevisionId: detail.extensionDefinitionRevisionId ?? detail.form?.binding.extensionDefinitionRevisionId,
    ...(extensionsChanged ? { extensionValues } : {}) }
}
