import { describe, expect, it } from 'vitest'
import { FieldType, type FormFieldConfig } from '@/api/lowcode'
import type { VersionedFormConfig } from '@/components/LowCodeFormRenderer/rendererVersion'
import { normalizeRendererConfig } from './compat'

function customField(overrides: Partial<FormFieldConfig> = {}): FormFieldConfig {
  return {
    id: 'field_custom',
    type: FieldType.CUSTOM,
    label: '业务组件',
    prop: 'customValue',
    ...overrides
  }
}

describe('LowCodeFormRendererFacade schema compatibility', () => {
  it('keeps legacy config identity when no compatibility conversion is needed', () => {
    const config: VersionedFormConfig = {
      fields: [customField({ props: { componentName: 'LegacyWidget' } })]
    }
    expect(normalizeRendererConfig(config)).toBe(config)
  })

  it('adapts top-level componentName for V1 without mutating persisted config', () => {
    const originalField = customField({
      componentName: 'CurrentWidget',
      props: { size: 'small' }
    })
    const config: VersionedFormConfig = { fields: [originalField] }

    const normalized = normalizeRendererConfig(config)

    expect(normalized).not.toBe(config)
    expect(normalized.fields[0]).not.toBe(originalField)
    expect(normalized.fields[0].props).toEqual({
      size: 'small',
      componentName: 'CurrentWidget'
    })
    expect(config.fields[0].props).toEqual({ size: 'small' })
  })

  it('does not overwrite historical props.componentName', () => {
    const config: VersionedFormConfig = {
      fields: [
        customField({
          componentName: 'CurrentWidget',
          props: { componentName: 'LegacyWidget' }
        })
      ]
    }

    const normalized = normalizeRendererConfig(config)
    expect(normalized).toBe(config)
    expect(normalized.fields[0].props?.componentName).toBe('LegacyWidget')
  })

  it('preserves rendererVersion while adapting fields', () => {
    const config: VersionedFormConfig = {
      rendererVersion: 'v2',
      fields: [customField({ componentName: 'CurrentWidget' })]
    }
    const normalized = normalizeRendererConfig(config)
    expect(normalized.rendererVersion).toBe('v2')
  })
})
