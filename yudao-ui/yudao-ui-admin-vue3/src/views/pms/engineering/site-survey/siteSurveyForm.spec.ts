import { describe, expect, it } from 'vitest'
import {
  extractSurveyValues,
  mergeSurveyValues,
  currentSurveyRules,
  surveyFields,
  hasStructuredSurveyLocation
} from './siteSurveyForm'

describe('site survey entity field mapping', () => {
  const entity = { id: 1, projectId: 2, code: 'SUR-1', name: '完整工勘', status: 0, version: 3 }
  it('removes legacy supplements from bound schemas while retaining conclusion and remarks', () => {
    const rules = [
      { type: 'div', props: { innerText: '原工勘补充说明（保留原字段内容）' } },
      ...surveyFields.map((field) => ({ type: 'input', field }))
    ]
    expect(currentSurveyRules(rules)).toEqual([
      { type: 'input', field: 'conclusion' },
      { type: 'input', field: 'remark' }
    ])
    expect(rules).toHaveLength(11)
  })
  it('preserves hidden historical values when saving the remaining fields', () => {
    const original = {
      ...entity,
      ...Object.fromEntries(surveyFields.map((field) => [field, `历史-${field}`]))
    }
    const saved = mergeSurveyValues(original, { conclusion: '新结论', remark: '' })
    for (const field of surveyFields.slice(0, 8)) expect(saved[field]).toBe(original[field])
    expect(saved.conclusion).toBe('新结论')
    expect(saved.remark).toBe('')
  })
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
