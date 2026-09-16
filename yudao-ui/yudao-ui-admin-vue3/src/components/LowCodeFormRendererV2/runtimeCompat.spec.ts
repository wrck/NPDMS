import { describe, expect, it } from 'vitest'
import { FieldType, type FormFieldConfig } from '@/api/lowcode'
import {
  buildFormCreateValidate,
  initMissingFieldDefaults,
  mergeExternalModelValue,
  resetFieldDefaults,
  resolveCustomComponentName,
  resolveFormCreateComponentType
} from './runtimeCompat'

function field(type: string, overrides: Partial<FormFieldConfig> = {}): FormFieldConfig {
  return {
    id: `field_${type}`,
    type,
    label: type,
    prop: type,
    ...overrides
  }
}

describe('LowCodeFormRendererV2 runtime compatibility', () => {
  it('maps all V1 field types to supported FormCreate component aliases', () => {
    expect(resolveFormCreateComponentType(field(FieldType.INPUT))).toBe('input')
    expect(resolveFormCreateComponentType(field(FieldType.TEXTAREA))).toBe('input')
    expect(resolveFormCreateComponentType(field(FieldType.PASSWORD))).toBe('input')
    expect(resolveFormCreateComponentType(field(FieldType.NUMBER))).toBe('InputNumber')
    expect(resolveFormCreateComponentType(field(FieldType.SELECT))).toBe('select')
    expect(resolveFormCreateComponentType(field(FieldType.RADIO))).toBe('radio')
    expect(resolveFormCreateComponentType(field(FieldType.CHECKBOX))).toBe('checkbox')
    expect(resolveFormCreateComponentType(field(FieldType.DATE))).toBe('DatePicker')
    expect(resolveFormCreateComponentType(field(FieldType.DATETIME))).toBe('DatePicker')
    expect(resolveFormCreateComponentType(field(FieldType.DATERANGE))).toBe('DatePicker')
    expect(resolveFormCreateComponentType(field(FieldType.SWITCH))).toBe('switch')
    expect(resolveFormCreateComponentType(field(FieldType.RATE))).toBe('rate')
    expect(resolveFormCreateComponentType(field(FieldType.SLIDER))).toBe('slider')
    expect(resolveFormCreateComponentType(field(FieldType.CASCADER))).toBe('cascader')
    expect(resolveFormCreateComponentType(field(FieldType.UPLOAD))).toBe('upload')
  })

  it('supports both historical props.componentName and canonical top-level componentName', () => {
    expect(
      resolveCustomComponentName(
        field(FieldType.CUSTOM, { componentName: 'TopLevelWidget' })
      )
    ).toBe('TopLevelWidget')
    expect(
      resolveCustomComponentName(
        field(FieldType.CUSTOM, {
          componentName: 'TopLevelWidget',
          props: { componentName: 'LegacyWidget' }
        })
      )
    ).toBe('LegacyWidget')
  })

  it('keeps V1 merge semantics for external model backfill', () => {
    const target: Record<string, unknown> = { a: 1, retained: 'keep' }
    mergeExternalModelValue(target, { a: 2, b: 3 })
    expect(target).toEqual({ a: 2, b: 3, retained: 'keep' })
  })

  it('initializes and resets defaults with the same V1 conventions', () => {
    const fields = [
      field(FieldType.INPUT, { prop: 'name', defaultValue: 'default-name' }),
      field(FieldType.CHECKBOX, { prop: 'tags' }),
      field(FieldType.INPUT, { prop: 'note' })
    ]
    const data: Record<string, unknown> = { name: 'external' }
    initMissingFieldDefaults(data, fields)
    expect(data).toEqual({ name: 'external', tags: [], note: '' })

    resetFieldDefaults(data, fields)
    expect(data).toEqual({ name: 'default-name', tags: [], note: '' })
  })

  it('merges required validation before custom validation rules', () => {
    const validate = buildFormCreateValidate(
      field(FieldType.INPUT, {
        label: '名称',
        placeholder: '请输入名称',
        required: true,
        rules: [{ min: 2, message: '至少两个字符' }]
      })
    )
    expect(validate).toEqual([
      {
        required: true,
        message: '请输入名称',
        trigger: ['blur', 'change']
      },
      { min: 2, message: '至少两个字符' }
    ])
  })
})
