import { describe, expect, it } from 'vitest'
import { extractSurveyValues, mergeSurveyValues, hasStructuredSurveyLocation } from './siteSurveyForm'

describe('site survey entity binding', () => {
  const entity = { id: 1, projectId: 2, code: 'SUR-1', name: '完整工勘', status: 0, version: 3,
    fieldCatalog: [{ code: 'cabinetReady', type: 'BOOLEAN', required: false }, { code: 'powerTypes', type: 'TEXT_LIST', required: false }],
    fieldBindings: { extra_cabinetReady: 'cabinetReady', power: 'powerTypes', count: 'count', labels: 'labels' },
    businessValues: { cabinetReady: false, powerTypes: ['AC'] }, extensionValues: { count: 0, labels: ['A'], untouched: 'keep' } }
  it('recognizes empty and structured location inputs', () => {
    expect(hasStructuredSurveyLocation({ address: {}, site: { siteType: 'CUSTOMER_SITE' }, siteLocation: { code: '' } })).toBe(false)
    expect(hasStructuredSurveyLocation({ site: { id: 3 } })).toBe(true)
  })
  it('uses supplied bindings for fixed properties and true extensions, without prefix inference', () => {
    expect(extractSurveyValues(entity)).toEqual({ extra_cabinetReady: false, power: ['AC'], count: 0, labels: ['A'] })
    expect(mergeSurveyValues(entity, extractSurveyValues(entity))).toEqual(entity)
  })
  it('preserves explicit false, zero, empty collections, and unsubmitted values', () => {
    const saved = mergeSurveyValues(entity, { power: [], labels: [], count: 0, extra_cabinetReady: false })
    expect(saved.businessValues).toEqual({ cabinetReady: false, powerTypes: [] })
    expect(saved.extensionValues).toEqual({ count: 0, labels: [], untouched: 'keep' })
  })
  it('rejects unbound identity and concurrency fields without changing the entity', () => {
    expect(() => mergeSurveyValues(entity, { id: 99, version: 99 })).toThrow('未绑定')
    expect(entity.id).toBe(1); expect(entity.version).toBe(3)
  })
  it('initializes missing or null collections as arrays so multi-selects can be edited', () => {
    const blank = { ...entity, businessValues: { powerTypes: null }, extensionValues: {} }
    expect(extractSurveyValues(blank)).toMatchObject({ power: [], extra_cabinetReady: null })
    const edited = mergeSurveyValues(blank, { power: ['AC', 'DC'] })
    expect(extractSurveyValues(edited).power).toEqual(['AC', 'DC'])
    expect(edited.extensionValues).toEqual({})
    expect(extractSurveyValues(mergeSurveyValues(edited, { power: [] })).power).toEqual([])
  })
})
