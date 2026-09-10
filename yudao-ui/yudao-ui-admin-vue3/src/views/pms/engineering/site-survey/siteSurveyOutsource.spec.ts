import { describe, expect, it } from 'vitest'
import {
  positiveShortcutId,
  outsourceShortcutRoute,
  outsourceDetailUrl
} from './siteSurveyOutsource'
import { mergeSurveyValues } from './siteSurveyForm'

describe('one outsourcing shortcut for a whole site survey', () => {
  it('carries only the saved survey identity into the original outsourcing route', () => {
    expect(outsourceShortcutRoute(30011)).toEqual({
      path: '/pms/engineering/procurement/eng-outsource',
      query: { siteSurveyId: '30011' }
    })
    expect(outsourceDetailUrl(44)).toBe('/pms/engineering/procurement/eng-outsource?requestId=44')
  })
  it('rejects absent, repeated and malformed route identities', () => {
    expect(positiveShortcutId('30011')).toBe(30011)
    for (const value of [undefined, ['1'], '0', '-1', '1.1', 'NaN', '9007199254740993'])
      expect(positiveShortcutId(value)).toBeUndefined()
  })
  it('dynamic fields do not overwrite the whole-survey shortcut or association', () => {
    const entity = {
      projectId: 1,
      code: 'S',
      name: 'survey',
      outsourceRequired: true,
      outsourceRequestId: 44
    }
    expect(
      mergeSurveyValues(entity, { outsourceRequired: false, outsourceRequestId: 55 })
    ).toMatchObject(entity)
  })
})
