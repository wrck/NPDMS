import { describe, expect, it } from 'vitest'
import {
  extractSurveyValues,
  mergeSurveyValues,
  surveyFields,
  hasStructuredSurveyLocation
} from './siteSurveyForm'

describe('site survey entity field mapping', () => {
  const entity = { id: 1, projectId: 2, code: 'SUR-1', name: '完整工勘', status: 0, version: 3 }
  it('does not mistake the location picker empty skeleton for a structured location', () => {
    expect(
      hasStructuredSurveyLocation({
        address: {},
        site: { siteType: 'CUSTOMER_SITE' },
        siteLocation: { code: '' }
      })
    ).toBe(false)
    expect(hasStructuredSurveyLocation({ site: { id: 3 } })).toBe(true)
    expect(hasStructuredSurveyLocation({ address: { countryName: '中国' } })).toBe(true)
  })
  it('round trips all original business columns and extension values without a second body', () => {
    const original = {
      ...entity,
      ...Object.fromEntries(surveyFields.map((field) => [field, `内容-${field}`])),
      formExtraValues: { extra_labels: ['A', 'B'], extra_count: 0, extra_ready: false }
    }
    expect(mergeSurveyValues(original, extractSurveyValues(original))).toEqual(original)
  })
  it('preserves explicit clears, zero, false, and empty collections', () => {
    const saved = mergeSurveyValues(entity, {
      powerSupply: '',
      extra_count: 0,
      extra_ready: false,
      extra_labels: []
    })
    expect(saved.powerSupply).toBe('')
    expect(saved.formExtraValues).toEqual({ extra_count: 0, extra_ready: false, extra_labels: [] })
  })
  it('does not let a configured field change identity, lifecycle, or concurrency fields', () => {
    const saved = mergeSurveyValues(entity, {
      id: 99,
      projectId: 99,
      status: 3,
      version: 99,
      code: 'HACK',
      name: 'HACK'
    })
    expect(saved).toMatchObject(entity)
    expect(saved.formExtraValues).toEqual({})
  })
})
