import { describe, expect, it } from 'vitest'
import { createPreviewData, uniqueCopyProp } from './designerState'

describe('designer editable state', () => {
  it('allocates a new answer key for repeated copies', () => {
    const fields = [{ prop: 'name' }]
    const first = uniqueCopyProp('name', fields)
    fields.push({ prop: first })
    const second = uniqueCopyProp('name', fields)
    expect(first).toBe('name_copy')
    expect(second).toBe('name_copy2')
  })
  it('respects existing manually assigned copy keys', () => {
    expect(uniqueCopyProp('name', [{ prop: 'name_copy' }, { prop: 'name_copy2' }])).toBe('name_copy3')
  })
  it('preserves false, zero and empty default values', () => {
    expect(
      createPreviewData([
        { prop: 'zero', defaultValue: 0 },
        { prop: 'flag', defaultValue: false },
        { prop: 'text', defaultValue: '' },
        { prop: 'missing' }
      ])
    ).toEqual({ zero: 0, flag: false, text: '', missing: '' })
  })
  it('does not share nested defaults with the editable model or another preview', () => {
    const fields = [{ prop: 'items', defaultValue: [{ value: 'original' }] }]
    const first = createPreviewData(fields)
    ;(first.items as Array<{ value: string }>)[0].value = 'edited'
    expect(fields[0].defaultValue[0].value).toBe('original')
    expect(createPreviewData(fields).items).toEqual([{ value: 'original' }])
  })
})
