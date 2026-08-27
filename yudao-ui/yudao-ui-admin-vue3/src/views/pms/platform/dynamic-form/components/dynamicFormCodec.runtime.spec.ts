import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import {
  decodeDynamicForm,
  encodeDynamicForm,
  restoreDynamicFormDesigner,
  sameDynamicFormPayload
} from './dynamicFormCodec'

describe('F-PLT-002 FormCreate REST codec', () => {
  it('adapts the existing string-array codec without dropping nested or unknown configuration', () => {
    const rules = [
      {
        type: 'mystery-control',
        field: 'unknown',
        children: [{ type: 'input', field: 'nested' }],
        on: { change: '[[FORM-CREATE-PREFIX-function(){return 1}-FORM-CREATE-SUFFIX]]' }
      }
    ]
    const designer = ref({
      getOption: () => ({ form: { labelPosition: 'top' }, global: { custom: true } }),
      getRule: () => rules,
      setOption: (option: unknown) => expect(option).toMatchObject({ global: { custom: true } }),
      setRule: (restored: unknown[]) => expect(restored).toHaveLength(1)
    })

    const encoded = encodeDynamicForm(designer)
    expect(Array.isArray(encoded.formRulesJson)).toBe(true)
    expect(encoded.formRulesJson[0]).toMatchObject({ type: 'mystery-control', field: 'unknown' })
    const decoded = decodeDynamicForm(encoded.formConfJson, encoded.formRulesJson)
    expect(decoded.rule).toHaveLength(1)
    restoreDynamicFormDesigner(designer, encoded.formConfJson, encoded.formRulesJson)
    expect(sameDynamicFormPayload(encoded, encoded)).toBe(true)
    expect(
      sameDynamicFormPayload(
        { formConfJson: { b: 2, a: { y: 2, x: 1 } }, formRulesJson: [{ b: 2, a: 1 }] },
        { formConfJson: { a: { x: 1, y: 2 }, b: 2 }, formRulesJson: [{ a: 1, b: 2 }] }
      )
    ).toBe(true)
    expect(sameDynamicFormPayload(encoded, { ...encoded, formRulesJson: [] })).toBe(false)
  })
})
