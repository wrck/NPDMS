import type { BusinessEntityFormData, FieldVO } from '@/api/pms/platform/businessmodel'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value))
export const declaredFormPresentation = (presentation: BusinessEntityFormData, fields: FieldVO[], initial: JsonObject) => {
  const layout = presentation.layout
  if (!layout) throw new Error('表单布局不可用')
  const fixed = new Map(fields.map(field => [field.code, field]))
  const extensions = new Map(presentation.definitions.map(field => [field.code, field]))
  const bindings = layout.binding.fieldBindings
  const values: JsonObject = {}, baseline: JsonObject = {}
  const editable = new Map<string, { code: string; extension: boolean }>()
  const mapped = new Set<string>()
  const visit = (rules: JsonObject[]): JsonObject[] => rules.flatMap(rule => {
    const field = typeof rule.field === 'string' ? rule.field : undefined
    const item = clone(rule)
    if (field) {
      const code = bindings[field], business = fixed.get(code), extension = extensions.get(code)
      if (!code || !business && !extension || business && !business.readable && !business.writable) return []
      if (rule.type === 'PmsFileArtifact') throw new Error('文件字段需要已声明的文件契约')
      mapped.add(code)
      const value = business ? (business.readable ? initial[code] : undefined) : presentation.extensions.fields[code]
      values[field] = value == null ? null : clone(value)
      baseline[field] = clone(values[field])
      delete item.value
      if (business && !business.writable) {
        item.props = { ...((item.props as JsonObject) || {}), disabled: true, readonly: true }
        item.validate = []
      } else editable.set(field, { code, extension: !!extension })
    }
    if (Array.isArray(item.children)) item.children = visit(item.children as JsonObject[])
    return [item]
  })
  const rule = visit(JSON.parse(layout.formRulesJson))
  const buildInput = (current: JsonObject): JsonObject => {
    const input: JsonObject = {}, changes: JsonObject = {}
    editable.forEach(({ code, extension }, field) => {
      if (!Object.hasOwn(current, field)) return
      const value = current[field] === '' || current[field] === undefined ? null : current[field]
      if (JSON.stringify(value) === JSON.stringify(baseline[field])) return
      ;(extension ? changes : input)[code] = value
    })
    if (Object.keys(changes).length) input.$extensions = {
      definitionRevisionId: layout.binding.extensionDefinitionRevisionId ?? presentation.extensions.definitionRevisionId,
      expectedVersion: presentation.extensions.version, values: changes
    }
    return input
  }
  return { rule, values, mapped, buildInput }
}
