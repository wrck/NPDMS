import type { Component } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { FieldType, type FormFieldConfig } from '@/api/lowcode'
import { buildFieldRule, type FieldRuleContext } from './fieldRules'

const upload: Component = { name: 'UploadFixture' }
function field(type: string, extra: Partial<FormFieldConfig> = {}): FormFieldConfig {
  return { id: type, type, prop: type, label: type, ...extra }
}
function context(extra: Partial<FieldRuleContext> = {}): FieldRuleContext {
  return { resolveComponent: () => undefined, uploadComponent: upload, onInput: vi.fn(), onChange: vi.fn(), ...extra }
}

describe('V2 field rules preserve the legacy form contract', () => {
  it.each([
    [FieldType.INPUT, 'input'], [FieldType.TEXTAREA, 'input'], [FieldType.PASSWORD, 'input'],
    [FieldType.NUMBER, 'InputNumber'], [FieldType.SELECT, 'select'], [FieldType.RADIO, 'radio'],
    [FieldType.CHECKBOX, 'checkbox'], [FieldType.DATE, 'DatePicker'],
    [FieldType.DATETIME, 'DatePicker'], [FieldType.DATERANGE, 'DatePicker'],
    [FieldType.SWITCH, 'switch'], [FieldType.RATE, 'rate'], [FieldType.SLIDER, 'slider'],
    [FieldType.CASCADER, 'cascader'], ['unknown', 'input']
  ])('maps %s without changing its data field or label', (type, componentType) => {
    const rule = buildFieldRule(field(type), context())
    expect(rule.type).toBe(componentType)
    expect(rule.field).toBe(type)
    expect(rule.title).toBe(type)
  })

  it('does not let same-named custom components leak between form instances', () => {
    const first: Component = { name: 'FirstCustomerPicker' }
    const second: Component = { name: 'SecondCustomerPicker' }
    const config = field(FieldType.CUSTOM, { componentName: 'CustomerPicker' })
    const a = buildFieldRule(config, context({ resolveComponent: () => first }))
    const b = buildFieldRule(config, context({ resolveComponent: () => second }))
    expect(a.component).toBe(first)
    expect(b.component).toBe(second)
    expect(a.component).not.toBe(b.component)
    expect(a.props?.field).toBe(config)
  })

  it('preserves the historical custom component name and input fallback', () => {
    const component: Component = { name: 'LegacyPicker' }
    const resolveComponent = vi.fn(() => component)
    const config = field(FieldType.CUSTOM, { componentName: 'NewPicker', props: { componentName: 'LegacyPicker' } })
    expect(buildFieldRule(config, context({ resolveComponent })).component).toBe(component)
    expect(resolveComponent).toHaveBeenCalledWith('LegacyPicker')
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    try {
      expect(buildFieldRule(config, context()).type).toBe('input')
      expect(warning).toHaveBeenCalledOnce()
    } finally {
      warning.mockRestore()
    }
  })

  it('preserves explicit component props instead of overwriting them with inferred defaults', () => {
    const textarea = buildFieldRule(field(FieldType.TEXTAREA, { props: { rows: 7, maxlength: 80 } }), context())
    expect(textarea.props).toMatchObject({ type: 'textarea', rows: 7, maxlength: 80 })
    const date = buildFieldRule(field(FieldType.DATE, { props: { type: 'week', valueFormat: 'YYYY-MM-DD' } }), context())
    expect(date.props).toMatchObject({ type: 'week', valueFormat: 'YYYY-MM-DD' })
    expect(buildFieldRule(field(FieldType.PASSWORD), context()).props?.type).toBe('password')
  })

  it('keeps option value types and required/custom validation without mutating the schema', () => {
    const options = Object.freeze([{ label: 'False', value: false }, { label: 'Zero', value: 0 }])
    const customRule = Object.freeze({ min: 2, message: 'too short' })
    const config = field(FieldType.SELECT, { required: true, props: { options }, rules: [customRule] })
    const rule = buildFieldRule(config, context())
    expect(rule.options).toEqual(options)
    expect(rule.options).not.toBe(options)
    expect(rule.validate).toHaveLength(2)
    expect(rule.validate?.[1]).toEqual(customRule)
    expect(rule.validate?.[1]).not.toBe(customRule)
  })

  it('updates form data before change callbacks, without treating the change payload as a model update', () => {
    const data: Record<string, unknown> = { customerId: 'old' }
    const onChange = vi.fn((_field, payload) => ({ payload, data: { ...data } }))
    const config = field(FieldType.CUSTOM, { prop: 'customerId', componentName: 'Picker' })
    const rule = buildFieldRule(config, context({
      resolveComponent: () => ({ name: 'Picker' }),
      onInput: (changedField, value) => { data[changedField.prop] = value },
      onChange
    }))
    ;(rule.on?.['update:modelValue'] as (value: unknown) => void)(23)
    ;(rule.on?.change as (value: unknown) => void)({ id: 23, name: 'Customer' })
    expect(data.customerId).toBe(23)
    expect(onChange).toHaveReturnedWith({ payload: { id: 23, name: 'Customer' }, data: { customerId: 23 } })
  })

  it('keeps title/divider native and upload outside the legacy model event chain', () => {
    expect(buildFieldRule(field(FieldType.TITLE), context())).toMatchObject({ type: 'h3', native: true })
    expect(buildFieldRule(field(FieldType.DIVIDER), context())).toMatchObject({ type: 'el-divider', native: true })
    const config = field(FieldType.UPLOAD, { props: { tip: 'Only PDF', action: '/configured-upload' } })
    const rule = buildFieldRule(config, context())
    expect(rule.component).toBe(upload)
    expect(rule.props?.field).toBe(config)
    expect(rule.on).toBeUndefined()
  })
})
