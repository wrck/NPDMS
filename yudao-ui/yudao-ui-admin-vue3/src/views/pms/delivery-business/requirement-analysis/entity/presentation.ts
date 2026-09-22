import content from '../demo-template.json'
import type { View, FormLayout } from '@/api/pms/engineering/requirement-analysis/entity'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'

const formKey = (code: string) => code.replace(/[A-Z]/g, value => `_${value}`).toUpperCase()

/** The built-in page renders business fields without any persisted template identity. */
export const basePresentation = (detail: View): FormLayout => {
  const mappings = Object.fromEntries(detail.fieldCatalog.map(field => [formKey(field.code), field.code]))
  const rules = JSON.parse(JSON.stringify(content.formRulesJson)) as JsonObject[]
  const retained = rules.filter(rule => mappings[String(rule.field)] || String(rule.field).endsWith('__ATTACHMENTS'))
  // Legacy extension fields still belong to the entity even when another layout is selected.
  if (detail.form) {
    const fixed = new Set(detail.fieldCatalog.map(field => field.code))
    const original = JSON.parse(detail.form.formRulesJson) as JsonObject[]
    const collect = (items: JsonObject[]) => items.forEach(rule => {
      const field = String(rule.field || '')
      const code = detail.form!.binding.fieldBindings[field]
      if (code && !fixed.has(code)) { mappings[field] = code; retained.push(rule) }
      else if (Array.isArray(rule.children)) collect(rule.children as JsonObject[])
    })
    collect(original)
  }
  return {
    binding: { fieldBindings: mappings, version: 0, extensionDefinitionRevisionId: detail.extensionDefinitionRevisionId },
    revisionNo: 0, formVersion: 0, engineCode: 'FORM_CREATE_ELEMENT_PLUS', designerVersion: '3', rendererVersion: '3',
    formConfJson: JSON.stringify(content.formConfJson), formRulesJson: JSON.stringify(retained),
    fields: retained.filter(rule => rule.field).map(rule => ({ fieldKey: String(rule.field),
      componentType: String(rule.type), controlledFile: rule.type === 'PmsFileArtifact', required: false }))
  }
}

export const canonicalValues = (detail: View, values: JsonObject): JsonObject => Object.fromEntries(
  Object.entries(values).map(([field, value]) => [detail.form?.binding.fieldBindings[field] || field, value])
)

export const displayedValues = (detail: View, values: JsonObject): JsonObject => detail.form
  ? Object.fromEntries(Object.entries(detail.form.binding.fieldBindings)
      .filter(([, code]) => Object.hasOwn(values, code)).map(([field, code]) => [field, values[code]]))
  : { ...values }

/** Keep edits to fields omitted by the new layout, including empty values and false/zero. */
export const mergePresentationEdits = (detail: View, retained: JsonObject, visibleChanges: JsonObject): JsonObject => {
  const result = { ...retained }
  Object.values(detail.form?.binding.fieldBindings || {}).forEach(code => delete result[code])
  return { ...result, ...canonicalValues(detail, visibleChanges) }
}
