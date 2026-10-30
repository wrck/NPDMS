import { describe, expect, it } from 'vitest'

import { parserConfigError, parserExamples } from '@/utils/parser-config'

describe('compatibility parser examples', () => {
  it('provides executable key-value configuration', () => {
    expect(parserConfigError('KEY_VALUE', parserExamples.KEY_VALUE.config)).toBe('')
    expect(parserExamples.KEY_VALUE.input).toContain('softVersion=1.2.3')
  })

  it('reports an invalid separator without changing parser type', () => {
    expect(parserConfigError('KEY_VALUE', '{"separator":""}')).toContain('separator')
    expect(parserConfigError('JSON', '{invalid')).toBe('')
  })
})
