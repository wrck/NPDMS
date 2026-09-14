import { describe, expect, it } from 'vitest'
import { fieldChanges, formatFieldValue } from './changePresentation'

describe('integration field comparison', () => {
  it('preserves zero, false, null and empty text as different values', () => {
    expect([0, false, null, '', undefined].map(formatFieldValue)).toEqual([
      '0',
      'false',
      'NULL（空值）',
      '空字符串',
      '—'
    ])
  })

  it('includes new adapter fields and fields missing from the source', () => {
    const changes = fieldChanges({
      object: 'custom',
      sourceKey: '001',
      action: 'UPDATED',
      before: { retained: 'a', removed: 1 },
      after: { retained: 'a', futureField: false }
    })
    expect(changes).toEqual([
      { field: 'retained', before: 'a', after: 'a', changed: false },
      { field: 'removed', before: '1', after: '—', changed: true },
      { field: 'futureField', before: '—', after: 'false', changed: true }
    ])
  })

  it('retains nested values without turning them into object placeholders', () => {
    expect(formatFieldValue({ codes: ['001', '002'] })).toBe('{"codes":["001","002"]}')
  })
})
