import { describe, expect, it } from 'vitest'
import { backwardDuration } from './backwardDuration'

describe('survey deadline backward duration', () => {
  it('includes both endpoints and handles leap days', () => {
    expect(backwardDuration('2026-12-31', 31)).toBe('2026-12-01')
    expect(backwardDuration('2026-12-31', 1)).toBe('2026-12-31')
    expect(backwardDuration('2028-03-01', 3)).toBe('2028-02-28')
  })
  it('does not invent a duration when inputs are missing or invalid', () => {
    expect(backwardDuration('2026-12-31')).toBe('')
    expect(backwardDuration('', 10)).toBe('')
    expect(backwardDuration('2026-12-31', 0)).toBe('')
    expect(backwardDuration('2026-12-31', 1.5)).toBe('')
  })
})
