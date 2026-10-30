import { expect, it } from 'vitest'
import { declaredFormPresentation } from './declaredFormPresentation'
import type { BusinessEntityFormData, FieldVO } from '@/api/pms/platform/businessmodel'
const fields: FieldVO[] = [
  { code: 'title', name: 'title', type: 'TEXT', required: true, readable: true, writable: true },
  { code: 'owner', name: 'owner', type: 'TEXT', required: false, readable: true, writable: false },
  { code: 'secret', name: 'secret', type: 'TEXT', required: false, readable: false, writable: false }
]
const presentation: BusinessEntityFormData = {
  layout: { binding: { formRevisionId: 8, extensionDefinitionRevisionId: 9, fieldBindings: { heading: 'title', author: 'owner', hidden: 'secret', notes: 'memo', items: 'devices' }, version: 1 },
    formConfJson: '{}', formRulesJson: JSON.stringify(['heading','author','hidden','notes','items','unknown'].map(field => ({ field, type: field === 'items' ? 'group' : 'input', value: 'layout value', validate: [{ required: true }] }))) },
  extensions: { definitionRevisionId: 9, fields: { memo: 'keep', devices: [{ serial: 'A' }] }, version: 3 },
  definitions: [{ code: 'memo', label: 'memo', type: 'TEXT', required: true }, { code: 'devices', label: 'devices', type: 'OBJECT_LIST', required: false }]
}
it('projects readable values and disables fixed read-only controls while excluding hidden and unmapped fields', () => {
  const form = declaredFormPresentation(presentation, fields, { title: 'Title', owner: 'Owner' })
  expect(form.values).toEqual({ heading: 'Title', author: 'Owner', notes: 'keep', items: [{ serial: 'A' }] })
  expect(form.rule.map(rule => rule.field)).toEqual(['heading','author','notes','items'])
  expect(form.rule.every(rule => !Object.hasOwn(rule, 'value'))).toBe(true)
  expect(form.rule[1].props).toEqual({ disabled: true, readonly: true })
  expect(form.buildInput({ ...form.values, author: 'forged', hidden: 'forged', unknown: 'forged' })).toEqual({})
})
it('maps aliases to one shared fixed/extension operation patch, preserving omitted values and explicit clear', () => {
  const form = declaredFormPresentation(presentation, fields, { title: 'Title', owner: 'Owner' })
  expect(form.buildInput(form.values)).toEqual({})
  expect(form.buildInput({ heading: 'Changed', notes: '', items: [{ serial: 'B' }] })).toEqual({ title: 'Changed', $extensions: { definitionRevisionId: 9, expectedVersion: 3, values: { memo: null, devices: [{ serial: 'B' }] } } })
  expect(form.buildInput({ heading: 'Changed' })).toEqual({ title: 'Changed' })
})
it('refuses a file rule without an Owner file contract', () => {
  const form = structuredClone(presentation)
  form.layout!.formRulesJson = '[{"type":"PmsFileArtifact","field":"notes"}]'
  expect(() => declaredFormPresentation(form, fields, {})).toThrow('文件字段')
})
